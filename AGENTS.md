# Repository guidelines

DocFlow is a three-student SWEN3 coursework project. Prioritize learning, the agreed course objectives, and code every teammate can explain.

- Keep implementations and setup straightforward. Add abstractions, dependencies, configuration switches, or infrastructure only for a concrete requirement or demonstrated need.
- Preserve the required layers, interfaces, mapping, ownership rules, validation, and meaningful tests. Document accepted limitations rather than adding production mechanisms or exhaustive failure scenarios.
- Docker Compose is the primary local and grading environment; verification must not need paid hosting or an AI account.
- Follow [architecture](docs/architecture.md) for design, [README](README.md) for setup/usage, and [sprint summary](docs/sprint-summary.md) for progress and verification. Update the relevant document instead of repeating it elsewhere.
- Read `backend/AGENTS.md` or `frontend/AGENTS.md` before changing that area. Add planned modules and tooling only with the features that need them.
- Keep credentials, local settings, uploaded documents, caches, and generated output out of Git. Preserve license notices and generated Maven Wrapper scripts.
- Use feature PRs to `dev`, release PRs to `main`, and another teammate's review. Run checks appropriate to the change before handoff.
