# Solo Developer Contribution Guidelines

This document outlines the simplified Git workflow tailored for a solo developer on the **PWB MiNi** project. It maximizes development speed while keeping the repository structured and ready for future CI/CD.

---

## 1. Branching Strategy (Dual-Branch Model)

Instead of complex Git Flow, we use a simplified two-branch model:

- **`develop` (Active Development)**:
  - Your primary workspace branch.
  - Commit and push directly to `develop` for daily tasks.
  - No need to create separate feature branches for small tasks.
- **`main` (Production / Stable Release)**:
  - Represents the code currently running in production.
  - Do not make direct commits to `main`.
  - When you are ready to release/deploy, merge `develop` into `main`. The push to `main` will trigger the deployment pipeline in the future.

---

## 2. Commit Message Guidelines

To keep the history searchable and prepare for automated changelog generation, we continue to follow the **Conventional Commits** specification.

### Format
`type(scope): description`

- **`type`** must be one of the following:
  - `feat`: A new feature.
  - `fix`: A bug fix.
  - `chore`: Maintenance tasks, dependencies updates, configuration changes.
  - `refactor`: Code change that neither fixes a bug nor adds a feature.
  - `docs`: Documentation updates.
  - `test`: Adding or correcting tests.
  - `perf`: Code changes that improve performance.
- **`scope`**: Specifies the affected sub-project:
  - `backend/<module>`: E.g., `backend/auth`, `backend/user`.
  - `frontend/<feature>`: E.g., `frontend/auth`, `frontend/dashboard`.
  - `docs/<doc-name>`: E.g., `docs/api`.
  - `root/<config>`: E.g., `root/gitignore`, `root/ci`.
- **`description`**: Brief description in the imperative mood (e.g., "add user registration").

### Examples
- `feat(backend/auth): add email verification via OTP`
- `fix(frontend/auth): resolve crash on login submit`
- `chore(root/gitignore): add agents path to ignore`

---

## 3. Daily Workflow

1. **Daily Development**: Work directly on the local `develop` branch.
2. **Save Work**: Commit and push directly to GitHub:
   ```bash
   git add .
   git commit -m "feat(backend/room): implement create room API"
   git push origin develop
   ```
3. **Release & Deploy**: When the code is stable and you want to deploy:
   ```bash
   git checkout main
   git merge develop
   ```
   *Verify everything builds and tests pass locally, then push to GitHub:*
   ```bash
   git push origin main
   git checkout develop
   ```
