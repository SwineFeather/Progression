# Quick Setup Guide

## 🚀 Getting Started in 5 Minutes

### 1. Download and Install
1. Download `PlayerStatsToMySQL-1.0.jar` from the releases
2. Place it in your server's `plugins` folder
3. Start your server once to generate the config file

### 2. Configure Database
1. Stop your server
2. Edit `plugins/PlayerStatsToMySQL/config.yml`
3. Update the MySQL settings:

```yaml
mysql:
  enabled: true
  url: "jdbc:mysql://localhost:3306/playerstats"
  user: "your_actual_username"
  password: "your_actual_password"
```

### 3. Create Database (if needed)
Run the `setup.sql` file in your MySQL server to create the database and tables.

### 4. Start Server
1. Start your server
2. Check the console for any error messages
3. The plugin should show "PlayerStatsToMySQL v1.0 enabled successfully!"

### 5. Test the Plugin
1. Join your server
2. Run `/sqlstats status` to check if everything is working
3. Run `/sqlstats sync` to sync existing player stats

## 🔧 Common Issues & Solutions

### Plugin Not Loading
- **Check console logs** for specific error messages
- **Verify database credentials** in config.yml
- **Ensure MySQL server is running**
- **Check if database exists**

### Database Connection Failed
```
[SEVERE] Database connection failed!
[SEVERE] Please check your MySQL settings in config.yml:
[SEVERE] - Ensure MySQL server is running
[SEVERE] - Verify database URL, username, and password
[SEVERE] - Check if database exists and user has proper permissions
```

**Solutions:**
1. Verify MySQL server is running
2. Check database URL format: `jdbc:mysql://host:port/database`
3. Ensure user has proper permissions
4. Test connection with MySQL client

### Config File Not Created
- Make sure the JAR file is in the correct `plugins` folder
- Check file permissions
- Try restarting the server

### Commands Not Working
- Check if you have the required permissions
- Use `/sqlstats help` to see available commands
- Ensure you're using the correct command syntax

## 📋 Required Permissions

Default permissions (OP has all):
- `playerstatstomysql.sqlstats.sync` - Sync stats
- `playerstatstomysql.sqlstats.export` - Export data
- `playerstatstomysql.sqlstats.view` - View stats
- `playerstatstomysql.sqlstats.reload` - Reload config
- `playerstatstomysql.sqlstats.status` - Check status
- `playerstatstomysql.sqlstats.help` - Show help

## 🎯 Quick Commands

```bash
/sqlstats status          # Check plugin status
/sqlstats sync            # Sync all player stats
/sqlstats export          # Export data to JSON
/sqlstats view <player> <category>  # View specific stats
/sqlstats help            # Show all commands
```

## 📞 Need Help?

1. Check the console logs for error messages
2. Verify your database connection
3. Read the full README.md for detailed documentation
4. Open an issue on GitHub with your error logs

---

**The plugin should now work! If you're still having issues, check the console logs for specific error messages.** 