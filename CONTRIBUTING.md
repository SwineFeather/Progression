# Contributing to PlayerStatsToMySQL

Thank you for your interest in contributing to PlayerStatsToMySQL! This document provides guidelines and information for contributors.

## 🤝 How to Contribute

### Reporting Bugs

Before creating bug reports, please check the existing issues to avoid duplicates. When creating a bug report, include:

- **Minecraft Version**: What version of Minecraft are you running?
- **Server Type**: Paper, Spigot, or other?
- **Plugin Version**: What version of PlayerStatsToMySQL?
- **Description**: A clear description of the bug
- **Steps to Reproduce**: Detailed steps to reproduce the issue
- **Expected Behavior**: What you expected to happen
- **Actual Behavior**: What actually happened
- **Logs**: Relevant console logs (use `logging.level: debug` in config)
- **Screenshots**: If applicable

### Suggesting Features

We welcome feature suggestions! When suggesting features:

- **Clear Description**: Explain what the feature should do
- **Use Case**: Why is this feature needed?
- **Implementation Ideas**: How might this be implemented? (optional)

### Code Contributions

#### Prerequisites

- Java 17 or higher
- Maven 3.6+
- Git
- A Minecraft server for testing

#### Development Setup

1. **Fork the Repository**
   ```bash
   git clone https://github.com/your-username/PlayerStatsToMySQL.git
   cd PlayerStatsToMySQL
   ```

2. **Build the Project**
   ```bash
   mvn clean compile
   ```

3. **Run Tests** (if available)
   ```bash
   mvn test
   ```

#### Coding Standards

- **Java Code Style**: Follow standard Java conventions
- **Comments**: Add comments for complex logic
- **Error Handling**: Include proper exception handling
- **Logging**: Use appropriate log levels (info, warning, severe)
- **Database**: Use prepared statements for all database operations

#### Commit Guidelines

Use conventional commit messages:

- `feat:` for new features
- `fix:` for bug fixes
- `docs:` for documentation changes
- `style:` for formatting changes
- `refactor:` for code refactoring
- `test:` for adding tests
- `chore:` for maintenance tasks

Examples:
```
feat: add support for custom stat categories
fix: resolve null pointer exception in export task
docs: update installation instructions
```

#### Pull Request Process

1. **Create a Feature Branch**
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Make Your Changes**
   - Write clean, well-documented code
   - Test your changes thoroughly
   - Update documentation if needed

3. **Commit Your Changes**
   ```bash
   git add .
   git commit -m "feat: add your feature description"
   ```

4. **Push to Your Fork**
   ```bash
   git push origin feature/your-feature-name
   ```

5. **Create a Pull Request**
   - Provide a clear description of your changes
   - Include any relevant issue numbers
   - Add screenshots if UI changes are involved

### Testing Guidelines

Before submitting a pull request, please:

1. **Test on Different Minecraft Versions** (if applicable)
2. **Test with Different Server Types** (Paper, Spigot)
3. **Test Database Operations** with various MySQL versions
4. **Test Plugin Interactions** with PlaceholderAPI and Towny
5. **Check for Memory Leaks** during extended testing

### Documentation

When contributing code, please:

- Update README.md if adding new features
- Add comments to complex code sections
- Update configuration examples if adding new options
- Document any new commands or permissions

## 🏗️ Project Structure

```
PlayerStatsToMySQL/
├── src/main/java/com/swinefeather/playerstatstomysql/
│   ├── Main.java                 # Main plugin class
│   ├── DatabaseManager.java      # Database operations
│   ├── StatSyncTask.java         # Stat synchronization
│   ├── ExportTask.java           # Data export functionality
│   └── PlaceholderManager.java   # PlaceholderAPI integration
├── src/main/resources/
│   ├── plugin.yml                # Plugin metadata
│   └── config.yml                # Default configuration
├── pom.xml                       # Maven configuration
└── README.md                     # Project documentation
```

## 🐛 Common Development Issues

### Database Connection Issues
- Ensure MySQL server is running
- Check credentials in config.yml
- Verify database permissions

### Compilation Errors
- Make sure you're using Java 17+
- Check Maven dependencies
- Verify package declarations match directory structure

### Plugin Loading Issues
- Check plugin.yml syntax
- Verify all required dependencies
- Test on a clean server instance

## 📞 Getting Help

If you need help with development:

1. **Check Existing Issues**: Search for similar problems
2. **Read Documentation**: Review README.md and code comments
3. **Ask Questions**: Open an issue with the "question" label
4. **Join Discussions**: Participate in existing issue discussions

## 🎯 Areas for Contribution

We're always looking for help with:

- **Bug Fixes**: Resolving reported issues
- **Feature Development**: Adding new functionality
- **Documentation**: Improving guides and examples
- **Testing**: Testing on different server setups
- **Performance**: Optimizing database operations
- **Compatibility**: Supporting more plugins or server types

## 📄 License

By contributing to PlayerStatsToMySQL, you agree that your contributions will be licensed under the MIT License.

---

Thank you for contributing to PlayerStatsToMySQL! 🎉 