# Contribution Guidelines

## Code Style

This project uses the opinionated Prettier code formatter. The [Prettier Java](https://github.com/jhipster/prettier-java) plugin is used to format Java code. The Prettier 
configuration is defined in the `.prettierrc` file. Please make sure to (at least) run Prettier before submitting a pull request. Preferred
would be to run Prettier on save in your IDE, which is supported by our preferred IDE IntelliJ IDEA.

To install Prettier Java, you can use the following command:

```bash
npm install prettier-plugin-java --save-dev
npm install prettier-plugin-kotlin --save-dev
```

Subsequently, IntelliJ IDEA can be configurated to use Prettier for formatting Java and Kotlin files. For this, you can use the 
[File Watchers](https://plugins.jetbrains.com/plugin/7177-file-watchers) plugin inside IntelliJ IDEA. After installing the plugin, you can add a new file watcher for Prettier or import the
provided `watchers.xml` file. File Watchers can be configured to run on save inside the _Tools → Actions on Save_ menu.