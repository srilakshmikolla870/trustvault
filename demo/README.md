# Synthetic demo setup

This guide creates safe, repeatable demo content without placing account passwords in source control.

1. Set a unique `POSTGRES_PASSWORD`, `TRUSTVAULT_BOOTSTRAP_ADMIN_EMAIL`, and `TRUSTVAULT_BOOTSTRAP_ADMIN_PASSWORD` in the private `.env` file. Set the bootstrap email and password before the first start if the database has no Administrator yet.
2. Generate a harmless PDF from the repository root with `.\demo\generate_sample_pdf.ps1`. The script writes `demo/TrustVault-Sample.pdf` by default.
3. Start the full local app with `docker compose --profile app up --build -d` and open `http://localhost:8081`.
4. Sign in as the bootstrap Administrator. Use **Create account** to create one synthetic Owner and one synthetic Viewer, using unique test passwords. Do not use real user accounts or passwords.
5. Sign in as the Owner, upload `demo/TrustVault-Sample.pdf`, then share it with the Viewer as READ. This seeds one document and an auditable sharing event using the actual application workflow.
6. For a public demo, create dedicated synthetic demo accounts after deployment. Use only credentials created for judging, with no personal or production access. Share them only through the challenge's designated demo-credentials field or other channel requested by the organizers; do not publish passwords in source control.

The script generates the PDF only; it does not seed accounts or bypass the application's authorization flow. The public demo is live at https://trustvault-demo-iy81.onrender.com. Its accounts must be created through the application; this repository does not automatically provision judge accounts or contain their credentials. Dedicated judge accounts still need to be created and tested in the deployed app.
