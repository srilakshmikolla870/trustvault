# Synthetic demo setup

This guide creates safe, repeatable demo content without placing account passwords in source control.

1. Set a unique `POSTGRES_PASSWORD`, `TRUSTVAULT_BOOTSTRAP_ADMIN_EMAIL`, and `TRUSTVAULT_BOOTSTRAP_ADMIN_PASSWORD` in the private `.env` file. Set the bootstrap email and password before the first start if the database has no Administrator yet.
2. Generate a harmless PDF using `.demogenerate_sample_pdf.ps1` from PowerShell.
3. Start the full local app with `docker compose --profile app up --build -d` and open `http://localhost:8081`.
4. Sign in as the bootstrap Administrator. Use **Create account** to create one synthetic Owner and one synthetic Viewer, using unique test passwords. Do not use real user accounts or passwords.
5. Sign in as the Owner, upload `demo/TrustVault-Sample.pdf`, then share it with the Viewer as READ. This seeds one document and an auditable sharing event using the actual application workflow.
6. For a public demo, create dedicated synthetic demo accounts after deployment and share their credentials only in the challenge README. Never publish local, personal, bootstrap, or production credentials.

The script generates the PDF only; it does not seed accounts or bypass the application's authorization flow. The public demo accounts and live URL still need to be provisioned by the project owner.
