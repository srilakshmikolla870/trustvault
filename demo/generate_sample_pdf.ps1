param([string] $OutputPath)

if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $OutputPath = Join-Path $PSScriptRoot 'TrustVault-Sample.pdf'
}

$encoding = [System.Text.Encoding]::ASCII
$document = [System.Text.StringBuilder]::new()
$offsets = [System.Collections.Generic.List[int]]::new()
$offsets.Add(0)
[void]$document.Append("%PDF-1.4`n")

$content = "BT /F1 18 Tf 72 720 Td (TrustVault Synthetic Demo Document) Tj ET`n"
$objects = @(
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>',
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
    "<< /Length $($encoding.GetByteCount($content)) >>`nstream`n${content}endstream"
)

for ($index = 0; $index -lt $objects.Count; $index++) {
    $number = $index + 1
    $offsets.Add($encoding.GetByteCount($document.ToString()))
    [void]$document.Append("$number 0 obj`n$($objects[$index])`nendobj`n")
}

$xrefOffset = $encoding.GetByteCount($document.ToString())
[void]$document.Append("xref`n0 $($offsets.Count)`n0000000000 65535 f `n")
for ($index = 1; $index -lt $offsets.Count; $index++) {
    [void]$document.Append("$($offsets[$index].ToString('D10')) 00000 n `n")
}
[void]$document.Append("trailer`n<< /Size $($offsets.Count) /Root 1 0 R >>`nstartxref`n$xrefOffset`n%%EOF`n")

[System.IO.File]::WriteAllBytes($OutputPath, $encoding.GetBytes($document.ToString()))
Write-Output "Created synthetic demo PDF: $([System.IO.Path]::GetFullPath($OutputPath))"
