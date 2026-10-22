$ErrorActionPreference = 'Stop'

$containerName = "trustvault-db-check-$([guid]::NewGuid().ToString('N').Substring(0, 8))"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$migrationDirectory = Join-Path $repoRoot 'backend\src\main\resources\db\migration'
$roleScript = Join-Path $PSScriptRoot 'production-postgres-roles.psql'
$createdContainer = $false

function Invoke-Docker {
    param([Parameter(Mandatory = $true)][string[]] $DockerArgs)
    & docker @DockerArgs
    $dockerExitCode = $LASTEXITCODE
    if ($dockerExitCode -ne 0) {
        throw "Docker command failed (exit $dockerExitCode): docker $($DockerArgs -join ' ')"
    }
}

try {
    Write-Host 'Starting a temporary PostgreSQL 16 database. It has no published ports or persistent volume.'
    Invoke-Docker -DockerArgs @(
        'run', '--rm', '--detach', '--name', $containerName, '--network', 'none',
        '--env', 'POSTGRES_DB=trustvault',
        '--env', 'POSTGRES_HOST_AUTH_METHOD=trust',
        'postgres:16-alpine'
    ) | Out-Null
    $createdContainer = $true

    $ready = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        & docker exec $containerName pg_isready -U postgres -d trustvault *> $null
        if ($LASTEXITCODE -eq 0) {
            $ready = $true
            break
        }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) { throw 'The temporary PostgreSQL database did not become ready in 60 seconds.' }

    # Mirror production's initial bootstrap login: it is temporarily a
    # superuser, then the hardening script disables that login. Run the
    # hardening script as the separate PostgreSQL administrator account.
    $bootstrapRole = "CREATE ROLE trustvault_app LOGIN SUPERUSER; ALTER DATABASE trustvault OWNER TO trustvault_app;"
    Invoke-Docker -DockerArgs @('exec', $containerName, 'psql', '-v', 'ON_ERROR_STOP=1', '-U', 'postgres', '-d', 'trustvault', '-c', $bootstrapRole)

    foreach ($migration in @('V1__initial_schema.sql', 'V2__protect_audit_events.sql')) {
        $migrationPath = Join-Path $migrationDirectory $migration
        Invoke-Docker -DockerArgs @('cp', $migrationPath, "${containerName}:/tmp/$migration")
        Write-Host "Applying $migration"
        Invoke-Docker -DockerArgs @('exec', $containerName, 'psql', '-v', 'ON_ERROR_STOP=1', '-U', 'trustvault_app', '-d', 'trustvault', '-f', "/tmp/$migration")
    }

    # production-postgres-roles.psql is documented to run after Flyway.
    # This isolated verifier applies SQL files directly, so create the Flyway
    # metadata table and record the two migrations to reproduce that state.
    $flywayHistory = @'
CREATE TABLE public.flyway_schema_history (
    installed_rank INTEGER NOT NULL PRIMARY KEY,
    version VARCHAR(50),
    description VARCHAR(200) NOT NULL,
    type VARCHAR(20) NOT NULL,
    script VARCHAR(1000) NOT NULL,
    checksum INTEGER,
    installed_by VARCHAR(100) NOT NULL,
    installed_on TIMESTAMP NOT NULL DEFAULT now(),
    execution_time INTEGER NOT NULL,
    success BOOLEAN NOT NULL
);
CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history (success);
INSERT INTO public.flyway_schema_history
    (installed_rank, version, description, type, script, installed_by, execution_time, success)
VALUES
    (1, '1', 'initial schema', 'SQL', 'V1__initial_schema.sql', 'trustvault_app', 0, TRUE),
    (2, '2', 'protect audit events', 'SQL', 'V2__protect_audit_events.sql', 'trustvault_app', 0, TRUE);
'@
    Write-Host 'Preparing Flyway history for the post-migration hardening check'
    Invoke-Docker -DockerArgs @('exec', $containerName, 'psql', '-v', 'ON_ERROR_STOP=1', '-U', 'trustvault_app', '-d', 'trustvault', '-c', $flywayHistory)

    Invoke-Docker -DockerArgs @('cp', $roleScript, "${containerName}:/tmp/production-postgres-roles.psql")
    Write-Host ''
    Write-Host 'Choose two temporary passwords when prompted below.'
    Write-Host 'They will be sent to psql through its input stream and will not be written to a file or command-line arguments.'
    $secureMigratorPassword = Read-Host 'Temporary password for trustvault_migrator' -AsSecureString
    $secureRuntimePassword = Read-Host 'Temporary password for trustvault_runtime' -AsSecureString
    $migratorPassword = [System.Net.NetworkCredential]::new('', $secureMigratorPassword).Password
    $runtimePassword = [System.Net.NetworkCredential]::new('', $secureRuntimePassword).Password
    if ([string]::IsNullOrWhiteSpace($migratorPassword) -or [string]::IsNullOrWhiteSpace($runtimePassword)) {
        throw 'Temporary role passwords must not be empty.'
    }

    # psql's \password prompts read from standard input when no TTY is present.
    # Use docker exec -i (without -t), so this works in regular PowerShell hosts.
    $passwordInput = @(
        $migratorPassword, $migratorPassword,
        $runtimePassword, $runtimePassword
    )
    $passwordInput | & docker exec -i $containerName psql -v ON_ERROR_STOP=1 -U postgres -d trustvault -f /tmp/production-postgres-roles.psql
    $roleSetupExitCode = $LASTEXITCODE
    $passwordInput = $null
    $migratorPassword = $null
    $runtimePassword = $null
    $secureMigratorPassword = $null
    $secureRuntimePassword = $null
    if ($roleSetupExitCode -ne 0) {
        throw "Docker role setup failed (exit $roleSetupExitCode). The temporary database will be removed."
    }

    $privilegeQuery = @'
SELECT
  has_table_privilege('trustvault_runtime', 'public.users', 'INSERT')
  AND has_table_privilege('trustvault_runtime', 'public.document_grants', 'INSERT')
  AND has_table_privilege('trustvault_runtime', 'public.audit_events', 'INSERT')
  AND NOT has_table_privilege('trustvault_runtime', 'public.audit_events', 'UPDATE')
  AND NOT has_table_privilege('trustvault_runtime', 'public.audit_events', 'DELETE')
  AND NOT has_table_privilege('trustvault_runtime', 'public.audit_events', 'TRUNCATE')
  AND NOT has_table_privilege('trustvault_runtime', 'public.flyway_schema_history', 'SELECT');
'@
    $privileges = & docker exec $containerName psql -At -U trustvault_migrator -d trustvault -c $privilegeQuery
    if ($LASTEXITCODE -ne 0 -or $privileges.Trim() -ne 't') {
        throw "Runtime-role privilege check failed: $privileges"
    }
    Write-Host 'PASS: runtime role has the required app writes and cannot modify audit rows, truncate tables, or access Flyway history.'

    $insertAudit = @'
INSERT INTO public.audit_events (id, actor_user_id, action, target_type, target_id, outcome, reason_code, request_id)
VALUES ('00000000-0000-0000-0000-000000000001', NULL, 'TEST', 'SYSTEM', NULL, 'ALLOWED', 'HARDENING_CHECK', 'temporary-role-check');
'@
    Invoke-Docker -DockerArgs @('exec', $containerName, 'psql', '-v', 'ON_ERROR_STOP=1', '-U', 'trustvault_runtime', '-d', 'trustvault', '-c', $insertAudit) | Out-Null
    Write-Host 'PASS: runtime role can append an audit event.'

    $mutateAudit = @'
UPDATE public.audit_events SET reason_code = 'CHANGED' WHERE request_id = 'temporary-role-check';
'@
    # Capture Docker through .NET instead of PowerShell's native-command
    # pipeline: some Windows PowerShell hosts turn the expected psql stderr
    # into a terminating NativeCommandError before checking its exit status.
    $dockerPath = (Get-Command docker -ErrorAction Stop).Source
    $processStartInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $processStartInfo.FileName = $dockerPath
    $dockerArgumentList = @('exec', $containerName, 'psql', '-v', 'ON_ERROR_STOP=1', '-U', 'trustvault_migrator', '-d', 'trustvault', '-c', $mutateAudit)
    $quotedDockerArguments = foreach ($argument in $dockerArgumentList) {
        '"' + ([string]$argument).Replace('"', '\"') + '"'
    }
    $processStartInfo.Arguments = $quotedDockerArguments -join ' '
    $processStartInfo.UseShellExecute = $false
    $processStartInfo.CreateNoWindow = $true
    $processStartInfo.RedirectStandardOutput = $true
    $processStartInfo.RedirectStandardError = $true
    $mutationProcess = [System.Diagnostics.Process]::new()
    $mutationProcess.StartInfo = $processStartInfo
    [void]$mutationProcess.Start()
    $mutationStdoutTask = $mutationProcess.StandardOutput.ReadToEndAsync()
    $mutationStderrTask = $mutationProcess.StandardError.ReadToEndAsync()
    $mutationProcess.WaitForExit()
    $mutationExitCode = $mutationProcess.ExitCode
    $mutationStdout = $mutationStdoutTask.GetAwaiter().GetResult()
    $mutationStderr = $mutationStderrTask.GetAwaiter().GetResult()
    $mutationProcess.Dispose()
    if ($mutationExitCode -eq 0 -or $mutationStderr -notmatch 'audit_events is append-only') {
        throw "Audit trigger check failed: expected the append-only trigger to reject UPDATE. Exit code: $mutationExitCode. Output: $mutationStdout $mutationStderr"
    }
    Write-Host 'PASS: database trigger rejects an audit-row UPDATE, even for the table owner.'
    Write-Host ''
    Write-Host 'DATABASE HARDENING CHECK PASSED.'
}
finally {
    if ($createdContainer) {
        & docker stop $containerName *> $null
    }
}
