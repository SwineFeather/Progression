# Town Balance Fix

## Problem
Town balances were showing as 0.0 in the plugin, even though towns had money in Towny.

## Root Cause
The plugin was only trying one method (`getAccount()`) to get the town balance, but different versions of Towny use different API methods to access town balances.

## Solution
Updated the `collectTownStats` method in `TownyManager.java` to try multiple balance retrieval methods:

### Methods Tried (in order):
1. **`getAccount()`** - Older Towny versions
2. **`getAccount().getHoldingBalance()`** - Newer Towny versions
3. **`getBalance()`** - Alternative method
4. **`getTreasury().getBalance()`** - Another alternative

### Code Changes:
```java
// Balance - try multiple methods to get town balance
double balance = 0.0;
try {
    // Try getAccount() first (older Towny versions)
    balance = (Double) townClass.getMethod("getAccount").invoke(town);
    logManager.debug("Got balance via getAccount(): " + balance);
} catch (Exception e1) {
    try {
        // Try getAccount().getHoldingBalance() (newer Towny versions)
        Object account = townClass.getMethod("getAccount").invoke(town);
        if (account != null) {
            balance = (Double) account.getClass().getMethod("getHoldingBalance").invoke(account);
            logManager.debug("Got balance via getAccount().getHoldingBalance(): " + balance);
        }
    } catch (Exception e2) {
        try {
            // Try getBalance() (alternative method)
            balance = (Double) townClass.getMethod("getBalance").invoke(town);
            logManager.debug("Got balance via getBalance(): " + balance);
        } catch (Exception e3) {
            try {
                // Try getTreasury() (another alternative)
                Object treasury = townClass.getMethod("getTreasury").invoke(town);
                if (treasury != null) {
                    balance = (Double) treasury.getClass().getMethod("getBalance").invoke(treasury);
                    logManager.debug("Got balance via getTreasury().getBalance(): " + balance);
                }
            } catch (Exception e4) {
                logManager.warning("Could not get balance for town " + townName + ": " + e4.getMessage());
                balance = 0.0;
            }
        }
    }
}
```

## Debug Command
Added a new debug command to help troubleshoot balance issues:

### Usage:
```
/townstats debug <town_name>
```

### What it does:
- Tests all balance retrieval methods
- Shows which methods work and which fail
- Displays the actual balance values
- Shows current cached data

### Example Output:
```
=== Debug Town Balance: MyTown ===
Testing different balance methods:
✓ getAccount(): 0.0
✗ getAccount().getHoldingBalance(): NoSuchMethodException
✓ getBalance(): 1500.0
✗ getTreasury().getBalance(): NoSuchMethodException
Current cached balance: 1500.0
```

## Files Modified:
1. **`src/main/java/com/swinefeather/progression/TownyManager.java`**
   - Updated `collectTownStats()` method to try multiple balance methods
   - Added debug logging for balance retrieval

2. **`src/main/java/com/swinefeather/progression/TownStatsCommands.java`**
   - Added `debugTownBalance()` method
   - Added debug command to help menu
   - Updated tab completion

## Testing
To test if the fix works:

1. **Check current balance**: `/townstats info <town_name>`
2. **Debug balance methods**: `/townstats debug <town_name>`
3. **Force sync**: `/townstats sync`
4. **Check again**: `/townstats info <town_name>`

## Expected Results
- Town balances should now show correct values
- Debug command will show which method works for your Towny version
- Balance-based XP calculations will work correctly

## Troubleshooting
If balances are still 0.0:

1. **Run debug command**: `/townstats debug <town_name>`
2. **Check Towny version**: Different versions use different API methods
3. **Check console logs**: Look for debug messages about balance retrieval
4. **Verify Towny economy**: Make sure towns actually have money in Towny

## Compatibility
This fix should work with:
- Towny 0.96+ (older versions)
- Towny 0.97+ (newer versions)
- Towny Advanced (if using different API methods)
- Various economy plugins (Vault, Reserve, etc.) 