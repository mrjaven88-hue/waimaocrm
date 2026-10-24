# AI Team Instructions

## Workflow
- Read the existing code before making changes.
- Confirm the task scope and acceptance criteria.
- Keep changes focused on one Linear issue.
- Use a separate Git branch for each task.
- Never commit secrets, credentials, or production data.
- Never deploy or modify production databases without approval.
- Run relevant tests and report actual results.
- Do not claim tests passed unless they were executed successfully.

## Required final report
1. What changed
2. Files changed
3. Tests run and actual results
4. Git branch and commit
5. Remaining risks or blockers

## Project constraints
- Preserve existing JEECG architecture and conventions.
- Use MySQL for database work.
- Support local file storage and preserve an adapter interface
  for future Tencent Cloud object storage integration.
- Keep student and teacher permissions separate.
