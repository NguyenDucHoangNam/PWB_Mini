# Contribution Guidelines

Thank you for contributing to PWB MiNi! To maintain code quality, enforce consistent rules, and keep our Git history clean in our Monorepo, please follow these guidelines.

---

## 1. Branching Strategy (Git Flow)

We use the standard Git Flow branching model. All active development must happen on feature branches.

### Branch Names
- **`main`**: Production branch. Only contains stable, tested release code. Never commit directly to `main`.
- **`develop`**: Integration branch. All features must merge here first.
- **`feature/<name>`**: For new features (e.g., `feature/user-auth`, `feature/payment-gateway`).
- **`bugfix/<name>`**: For bug fixes (e.g., `bugfix/login-crash`).
- **`hotfix/<name>`**: For urgent production hotfixes. Branched from `main`, merged to both `main` and `develop`.
- **`release/v<version>`**: For preparing releases (e.g., `release/v1.0.0`).

---

## 2. Commit Message Guidelines

We follow the [Conventional Commits](https://www.conventionalcommits.org/) specification.

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
  - `style`: Changes that do not affect the meaning of the code (white-space, formatting, etc.).
- **`scope`** (Mandatory for Monorepo): Specifies the affected sub-project to keep history clear. It must use the folder name path:
  - `backend/<module>`: E.g. `backend/auth`, `backend/user`, `backend/room`.
  - `frontend/<feature>`: E.g. `frontend/auth`, `frontend/dashboard`.
  - `docs/<doc-name>`: E.g. `docs/api`.
  - `root/<config>`: E.g. `root/gitignore`, `root/ci`.
- **`description`**: Brief description in the imperative mood (e.g., "add user registration", NOT "added user registration").

### Examples
- `feat(backend/auth): add email verification via OTP`
- `fix(frontend/auth): resolve crash on login submit`
- `chore(root/gitignore): add agents path to ignore`
- `docs(docs/live-room): update setup instruction`

---

## 3. Development & Pull Request Workflow

Please follow this step-by-step workflow when writing code:

1. **Synchronize**: Make sure your local `develop` branch is up to date:
   ```bash
   git checkout develop
   git pull origin develop
   ```
2. **Create Branch**: Create your feature branch from `develop`:
   ```bash
   git checkout -b feature/your-feature-name
   ```
3. **Code & Test**: Write code following the project guidelines. Run tests locally to ensure everything works:
   - Backend: `./mvnw clean test`
   - Frontend: `pnpm run test`
4. **Commit**: Commit your changes locally using conventional commits:
   ```bash
   git add .
   git commit -m "feat(backend/auth): implement oauth2 login"
   ```
5. **Push & PR**: Push your branch to GitHub and open a Pull Request (PR) to the `develop` branch:
   ```bash
   git push -u origin feature/your-feature-name
   ```
6. **Code Review & CI**: Wait for the automated CI build tests to pass and get approval from team members.
7. **Merge**: Once approved, merge the PR into `develop` and delete your feature branch.
