# Language Policy

- **English-only content**: All code, documentation, and comments must be written in English.
- **Applies regardless of chat language**: This rule holds no matter what language is used in conversations or any other context.
- **Exceptions by explicit instruction**: Use a non‑English language only when there is a clear, explicit instruction to do so.
- **Preserve existing non‑English**: If existing code/docs/comments are already in a non‑English language, do not change them unless explicitly instructed.
- **Developer chat language**: Communicate with the developer in the same language used in their requests, while keeping all code, docs, and comments in English.

This policy ensures consistency and clarity across the codebase and documentation while remaining responsive and accessible in developer conversations.

# Further development

See [TODO.md](TODO.md) file for the further dvelopment goals and tasks.

Marks
- `[ ]` - incomplete task
- `[X]` - completed task

While it will be OK to add new tasks to this file for you, you must never mark tasks complete or incomplete.

All Visual Studio Code configuration for this project must reside in `.devcontainer/devcontainer.json`.


# Polygon experiments

- The `polygon/` directory keeps lightweight experimental code that is not part of the main product.
- Each experiment lives in its own subfolder named with a three-digit prefix followed by a short label (for example, `999.simple`).
- When creating a new experiment, choose the next prefix by taking the smallest existing experiment prefix and subtracting one (e.g., if `999.*` is present, the next becomes `998.new-idea`).
- Keep experiments self-contained inside their folders so they can be removed without affecting the core project.

# Additional custom information

For the additional information about the project, check [CUSTOM.md](CUSTOM.md) file.
This contains instructions related to particular dev environments, and not saved in git repo.

- The `andriod/` directory contains a standalone Android application that is maintained separately from the main project.

# Dockerfile changes

- When modifying the `Dockerfile`, keep layer caching and rebuild speed in mind—place new commands as close to the bottom as practical so container rebuilds stay fast.
