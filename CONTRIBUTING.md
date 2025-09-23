# Contribution Guidelines

To contribute, please refer to the guidelines below.

## Development Setup

We highly recommend using IntelliJ IDEA for development. Opening the project in IntelliJ IDEA will automatically configure the project for
development.

Additionally, we highly recommend using the [Pkl IntellJ Plugin](https://pkl-lang.org/intellij/current/index.html) to provide syntax
highlighting and general support for Pkl files. Using this plugin, the provided CSML Pkl unit tests will automatically correctly import
the CSM package provided by the project.

## Code Style

This project uses the opinionated Prettier code formatter. The [Prettier Java](https://github.com/jhipster/prettier-java) plugin is used to
format Java code. The Prettier configuration is defined in the `.prettierrc` file. Please make sure to (at least) run Prettier before
submitting a pull request. Preferred would be to run Prettier on save in your IDE, which is supported by our preferred IDE IntelliJ IDEA.

To install Prettier Java, you can use the following command:

```bash
npm install prettier-plugin-java --save-dev
```

Subsequently, IntelliJ IDEA can be configured to use Prettier for formatting Java files. For this, you can use
the [File Watchers](https://plugins.jetbrains.com/plugin/7177-file-watchers) plugin inside IntelliJ IDEA. After installing the plugin, you
can add a new file watcher for Prettier or import the provided `watchers.xml` file. File Watchers can be configured to run on save inside
the _Tools → Actions on Save_ menu.

For Kotlin formatting, we use [ktfmt](https://github.com/cortinico/ktfmt-gradle) and the Google style. Formatting is checked by the CI.

## Pull Requests

We accept pull requests for bug fixes and new features from forks. Please make sure to add unit tests for your changes. The CI will
automatically build and test your changes.

Make sure that your both the commits in your pull request and the pull request title are formatted according to the
[Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) specification. Commit linting is enforced by the CI. Squashed commits
upon merging are also enforced by the CI and will receive the pull request title as the commit message. We use Conventional Commits to
generate the changelogs.

## Code Guidelines

This project uses Java for the majority of the code. However, we only accept new contributions in Kotlin, and where possible, Java
implementations should be reimplemented in Kotlin.

Public functions and members should be properly documented using Javadoc or KDoc. Private functions and members should be commented in case
they are not self-explanatory.

Please follow the style of the existing code.