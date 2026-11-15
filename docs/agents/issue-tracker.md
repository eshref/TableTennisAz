# Issue tracker: GitHub

Issues and specs live in GitHub Issues for `eshref/TableTennisAz`. Use the `gh` CLI.

## Authentication and target

In this sandbox, prefix `gh` commands with `GH_TOKEN=proxy-managed`. This is a non-secret placeholder that lets the CLI run; the sandbox proxy supplies the actual authentication.

Before the first tracker write, verify the target with `gh repo view eshref/TableTennisAz --json nameWithOwner`. Pass `--repo eshref/TableTennisAz` to issue and label commands; use the full repository path in API commands.

## Operations

- Publish a spec or ticket: `gh issue create --repo eshref/TableTennisAz --title "<title>" --body-file <body-file>`.
- Read the complete issue and comments: `gh issue view <number> --repo eshref/TableTennisAz --json number,title,body,labels,comments`.
- List issues: `gh issue list --repo eshref/TableTennisAz --state open --json number,title,body,labels`. Add label or state filters as needed.
- Comment: `gh issue comment <number> --repo eshref/TableTennisAz --body-file <body-file>`.
- Change labels: `gh issue edit <number> --repo eshref/TableTennisAz --add-label "<label>"` or `--remove-label "<label>"`.
- Create a missing configured label: `gh label create "<label>" --repo eshref/TableTennisAz --description "<description>" --color "<hex>"`. Preserve existing labels.
- Close an issue: post its explanation as a comment first, then use `gh issue close <number> --repo eshref/TableTennisAz`.

Use real multiline body files. Check the configured triage-role mapping before applying labels.

## Ticket relationships

Use native GitHub sub-issues and blocking relationships where supported. With an older CLI, use these API operations:

- Read an issue's numeric database ID: `gh api repos/eshref/TableTennisAz/issues/<number> --jq .id`.
- Add a child to a parent: `gh api --method POST repos/eshref/TableTennisAz/issues/<parent-number>/sub_issues -F sub_issue_id=<child-database-id>`.
- Add a blocker to a child: `gh api --method POST repos/eshref/TableTennisAz/issues/<child-number>/dependencies/blocked_by -F issue_id=<blocker-database-id>`.
- Check open blockers: `gh api repos/eshref/TableTennisAz/issues/<number> --jq .issue_dependencies_summary.blocked_by`.

Database IDs differ from issue numbers and node IDs. If native relationships are unavailable, record `Part of #<parent>` and `Blocked by: #<blocker>` in ticket bodies. A ticket is unblocked when all its blockers are closed.

## Pull requests as a triage surface

**PRs as a request surface: no.**
