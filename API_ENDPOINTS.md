# Progression API Endpoints

This document describes all the REST API endpoints available for the Progression plugin's level and achievement system.

## Base URL
All endpoints are relative to your Supabase project URL: `https://your-project.supabase.co/rest/v1/`

## Authentication
All endpoints require the Supabase API key in the headers:
```
apikey: your-supabase-anon-key
Authorization: Bearer your-supabase-anon-key
```

## Player Endpoints

### Get Player Stats
**GET** `/player_stats?player_uuid=eq.{uuid}`

Returns the complete stats for a specific player.

**Response:**
```json
[
  {
    "id": 1,
    "player_uuid": "123e4567-e89b-12d3-a456-426614174000",
    "stats": {
      "custom_minecraft_play_time": {"rank": 5, "value": 720000},
      "mine_ground": {"rank": 3, "value": 25000},
      "kill_any": {"rank": 2, "value": 500}
    },
    "last_updated": 1640995200000
  }
]
```

### Get Player Level Data
**GET** `/players?uuid=eq.{uuid}`

Returns the level and XP data for a specific player.

**Response:**
```json
[
  {
    "uuid": "123e4567-e89b-12d3-a456-426614174000",
    "name": "PlayerName",
    "level": 15,
    "total_xp": 25000,
    "last_level_up": 1640995200000,
    "last_seen": 1640995200000
  }
]
```

### Get Player Achievements
**GET** `/unlocked_achievements?player_uuid=eq.{uuid}`

Returns all unlocked achievements for a specific player.

**Response:**
```json
[
  {
    "id": 1,
    "player_uuid": "123e4567-e89b-12d3-a456-426614174000",
    "achievement_id": "time_lord",
    "tier": 3,
    "unlocked_at": "2024-01-01T12:00:00Z",
    "xp_awarded": 1500
  }
]
```

### Get Player Achievement Progress
**GET** `/achievement_progress?player_uuid=eq.{uuid}`

Returns detailed achievement progress for a specific player, including both unlocked and locked achievements.

**Response:**
```json
[
  {
    "achievement_id": "time_lord",
    "achievement_name": "Time Lord",
    "achievement_description": "Accumulate playtime on the server",
    "stat": "custom_minecraft_play_time",
    "achievement_color": "#f59e0b",
    "achievement_type": "player",
    "tier": 1,
    "tier_name": "Newcomer",
    "tier_description": "1 hour of playtime",
    "threshold": 72000,
    "icon": "⏰",
    "points": 50,
    "player_uuid": "123e4567-e89b-12d3-a456-426614174000",
    "unlocked_at": "2024-01-01T12:00:00Z",
    "xp_awarded": 500,
    "status": "unlocked"
  }
]
```

## Leaderboard Endpoints

### Get Level Leaderboard
**GET** `/level_leaderboard?limit={number}`

Returns the top players by level and XP.

**Parameters:**
- `limit` (optional): Number of players to return (default: 10)

**Response:**
```json
[
  {
    "uuid": "123e4567-e89b-12d3-a456-426614174000",
    "name": "TopPlayer",
    "level": 25,
    "total_xp": 50000,
    "level_title": "Master",
    "level_description": "A true master of the server",
    "level_color": "#ffd700",
    "last_seen": 1640995200000
  }
]
```

### Get Award Leaderboard
**GET** `/award_leaderboard?limit={number}`

Returns the top players by award points.

**Parameters:**
- `limit` (optional): Number of players to return (default: 10)

**Response:**
```json
[
  {
    "uuid": "123e4567-e89b-12d3-a456-426614174000",
    "name": "TopPlayer",
    "total_points": 1500.5,
    "total_medals": 25,
    "gold_medals": 10,
    "silver_medals": 8,
    "bronze_medals": 7,
    "last_seen": 1640995200000
  }
]
```

### Get Achievement-Specific Leaderboard
**GET** `/award_leaderboard?award_id=eq.{achievement_id}&limit={number}`

Returns the top players for a specific achievement.

**Parameters:**
- `award_id`: The achievement ID to filter by
- `limit` (optional): Number of players to return (default: 10)

**Response:**
```json
[
  {
    "award_id": "time_lord",
    "award_name": "Time Lord",
    "player_uuid": "123e4567-e89b-12d3-a456-426614174000",
    "player_name": "TopPlayer",
    "points": 300.0,
    "medal": "gold",
    "tier": "6",
    "achieved_at": "2024-01-01T12:00:00Z",
    "rank": 1
  }
]
```

## Achievement System Endpoints

### Get All Achievement Definitions
**GET** `/achievement_definitions`

Returns all available achievement definitions.

**Response:**
```json
[
  {
    "id": 1,
    "achievement_id": "time_lord",
    "name": "Time Lord",
    "description": "Accumulate playtime on the server",
    "stat": "custom_minecraft_play_time",
    "color": "#f59e0b",
    "achievement_type": "player"
  }
]
```

### Get Achievement Tiers
**GET** `/achievement_tiers?achievement_id=eq.{achievement_id}`

Returns all tiers for a specific achievement.

**Response:**
```json
[
  {
    "id": 1,
    "achievement_id": "time_lord",
    "tier": 1,
    "name": "Newcomer",
    "description": "1 hour of playtime",
    "threshold": 72000,
    "icon": "⏰",
    "points": 50
  }
]
```

### Get Level Definitions
**GET** `/level_definitions?level_type=eq.{type}`

Returns level definitions for players or towns.

**Parameters:**
- `level_type`: Either "player" or "town"

**Response:**
```json
[
  {
    "id": 1,
    "level_type": "player",
    "level": 1,
    "xp_required": 0,
    "title": "Novice",
    "description": "Just starting your journey",
    "color": "#6b7280"
  }
]
```

## Town Endpoints

### Get Town Achievements
**GET** `/unlocked_achievements?town_name=eq.{town_name}`

Returns all unlocked achievements for a specific town.

**Response:**
```json
[
  {
    "id": 1,
    "town_name": "MyTown",
    "achievement_id": "population_growth",
    "tier": 3,
    "unlocked_at": "2024-01-01T12:00:00Z",
    "xp_awarded": 1500
  }
]
```

## Data Modification Endpoints

### Update Player Level
**PUT** `/players`

Updates a player's level and XP data.

**Request Body:**
```json
{
  "uuid": "123e4567-e89b-12d3-a456-426614174000",
  "name": "PlayerName",
  "level": 15,
  "total_xp": 25000,
  "last_level_up": 1640995200000
}
```

**Headers:**
```
Prefer: resolution=merge-duplicates
```

### Sync Unlocked Achievement
**PUT** `/unlocked_achievements`

Records an unlocked achievement for a player or town.

**Request Body:**
```json
{
  "player_uuid": "123e4567-e89b-12d3-a456-426614174000",
  "achievement_id": "time_lord",
  "tier": 3,
  "xp_awarded": 1500
}
```

**Headers:**
```
Prefer: resolution=merge-duplicates
```

## Error Responses

All endpoints return standard HTTP status codes:

- **200 OK**: Request successful
- **400 Bad Request**: Invalid request parameters
- **401 Unauthorized**: Invalid API key
- **404 Not Found**: Resource not found
- **500 Internal Server Error**: Server error

Error responses include a JSON object with error details:

```json
{
  "error": "Error message",
  "details": "Additional error details"
}
```

## Rate Limiting

The API implements rate limiting to prevent abuse:
- Maximum 100 requests per minute per IP
- Maximum 1000 requests per hour per IP

## Pagination

For endpoints that return lists, you can use pagination:

**GET** `/level_leaderboard?limit=10&offset=20`

**Parameters:**
- `limit`: Number of records to return (max 100)
- `offset`: Number of records to skip

## Filtering

Most endpoints support filtering using Supabase's query syntax:

**Examples:**
- `?level=gt.10` - Players with level greater than 10
- `?total_xp=gte.10000` - Players with XP greater than or equal to 10000
- `?achievement_type=eq.player` - Player achievements only
- `?unlocked_at=gte.2024-01-01` - Achievements unlocked since January 1, 2024

## Sorting

You can sort results using the `order` parameter:

**Examples:**
- `?order=level.desc` - Sort by level descending
- `?order=total_xp.desc` - Sort by XP descending
- `?order=unlocked_at.desc` - Sort by unlock date descending

## Example Usage

### Get Top 10 Players by Level
```bash
curl -X GET "https://your-project.supabase.co/rest/v1/level_leaderboard?limit=10" \
  -H "apikey: your-supabase-anon-key" \
  -H "Authorization: Bearer your-supabase-anon-key"
```

### Get Player's Achievement Progress
```bash
curl -X GET "https://your-project.supabase.co/rest/v1/achievement_progress?player_uuid=eq.123e4567-e89b-12d3-a456-426614174000" \
  -H "apikey: your-supabase-anon-key" \
  -H "Authorization: Bearer your-supabase-anon-key"
```

### Update Player Level
```bash
curl -X PUT "https://your-project.supabase.co/rest/v1/players" \
  -H "apikey: your-supabase-anon-key" \
  -H "Authorization: Bearer your-supabase-anon-key" \
  -H "Content-Type: application/json" \
  -H "Prefer: resolution=merge-duplicates" \
  -d '{
    "uuid": "123e4567-e89b-12d3-a456-426614174000",
    "name": "PlayerName",
    "level": 15,
    "total_xp": 25000,
    "last_level_up": 1640995200000
  }'
```

## Webhook Integration

The plugin can send webhook notifications for:
- Level ups
- Achievement unlocks
- Award achievements

Configure webhooks in the plugin configuration file.

## Database Schema

For the complete database schema, see `supabase_schema.sql`.

## Support

For API support and questions, please refer to the plugin documentation or create an issue on the GitHub repository. 