# Contributing to NewProxy

Welcome to NewProxy! This document is a guideline about how to contribute to **NewProxy**.

If you find something incorrect or missing, please leave your suggestions or comments.

---

# Before you get started

Please make sure to read and observe our [CODE_OF_CONDUCT](./CODE_OF_CONDUCT.MD).

---

# Contributing

NewProxy welcome new participants of any role, including user, contributor, etc.

Newcomers are welcome to contribute to NewProxy project. If you are interested in contributing,
please read the document list below.

---

## Open or pickup an issue for preparation

If you find a typo in a document, find a bug in code or want new features, or want to give suggestions, please
open an issue on GitHub to report it.

Please note that any PR must be associated with a valid issue. Otherwise, the PR will be rejected.

---

## Begin your contribution

Now if you want to contribute, please create a new pull request.

Branch `develop` is used as the development branch (default), which indicates that this is an unstable branch.
For more information about the branch model, please refer to
[A successful git branch model](https://nvie.com/posts/a-successful-git-branching-model/).

Now, if you are ready to create PR, here is the workflow for contributors:

1. Fork to your own repository
2. Clone fork to a local repository
3. Create a new branch and work on it
4. Keep your branch in sync
5. Commit your changes (make sure your commit message is concise)
6. Push your commits to your forked repository
7. Create a pull request to `develop` branch

When creating pull request:

1. Please create the request to `develop` branch
2. Please make sure the PR has a corresponding issue
3. If your PR contains large changes, e.g. component refactor or new components, please write detailed documents about
   its design and usage.
4. Note that a single PR should not be too large. If heavy changes are required, it's better to separate the changes
   to a few individual PRs.
5. After creating a PR, one or more reviewers will be assigned to the pull request.
6. Before merging a PR, squash any fix review feedback, typo, merged and rebased sorts of commits. The final commit
   message should be clear and concise.

---

## Code review guidance

Code will be reviewed before PR is merged. If sometimes code is not reviewed timely, volunteers for code review are
always welcome.

Some principles:

* Readability: Important code should be well-documented. API should have Javadoc. Code style should be complied with
  the existing one.
* Elegance: New functions, classes or components should be well-designed.
* Testability: Unit tests should cover at least 80% of the new code.
* Maintainability: Code should be easy to maintain.

---
