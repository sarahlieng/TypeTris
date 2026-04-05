# TypeTris
 
A Tetris-style typing game built with Java Swing where falling word blocks must be typed to unlock, then placed on the grid. Combines the strategic puzzle gameplay of Tetris with typing practice to improve WPM and accuracy.
 
## Table of Contents
 
- [Description](#description)
- [Features](#features)
- [Required Libraries and Tools](#required-libraries-and-tools)
- [Building from Source](#building-from-source)
- [Running the Application](#running-the-application)
- [User Guide](#user-guide)
- [Account Information](#account-information)
- [Parental Controls](#parental-controls)
- [Additional Notes for TAs](#additional-notes-for-tas)
 
---
 
## Description
 
TypeTris is a desktop typing game that combines Tetris mechanics with typing practice. Word-shaped blocks fall from the top of the screen. Players must type the word correctly to "unlock" the block, then use arrow keys to position and rotate it before it lands. Clear rows by filling them completely, build combos, and compete for high scores on the leaderboard.
 
The game tracks typing statistics including words per minute (WPM), accuracy percentage, and error count. Players progress through difficulty tiers (Easy, Medium, Hard) with sub-levels that increase fall speed and word length.
 
---
 
## Features
 
- **User Accounts**: Register and login with username/password authentication
- **Difficulty Tiers**: Easy, Medium, and Hard with 3 sub-levels each
- **Leaderboard**: Global high scores tracking
- **Typing Statistics**: Real-time WPM, accuracy, and error tracking
- **Power-ups**: Freeze, Easy Mode, and Blast abilities
- **Lives System**: Multiple lives before game over
- **Progress Tracking**: Unlockable sub-levels based on completion
- **Parental Controls**: Admin-accessible controls for managing gameplay
 
---
 
## Required Libraries and Tools
 
### Build Requirements
 
| Tool/Library | Version | Purpose |
|--------------|---------|---------|
| Java JDK | 17 or higher | Compilation and runtime |
| Maven | 3.8.0+ | Build automation and dependency management |
| SQLite JDBC | 3.45.0+ | Database connectivity (managed by Maven) |
 
### Runtime Requirements
 
| Requirement | Version | Notes |
|-------------|---------|-------|
| Java Runtime (JRE) | 17 or higher | Required to run the compiled JAR |
 
### Dependencies (Managed by Maven)
 
The following dependencies are automatically downloaded by Maven:
 
```xml
<dependencies>
    <!-- SQLite JDBC Driver -->
    <dependency>
        <groupId>org.xerial</groupId>
        <artifactId>sqlite-jdbc</artifactId>
        <version>3.45.1.0</version>
    </dependency>
</dependencies>
```
 
---
 
## Building from Source
 
### Prerequisites
 
1. **Install Java JDK 17+**
   - Download from [Oracle JDK](https://www.oracle.com/java/technologies/downloads/) or [OpenJDK](https://adoptium.net/)
   - Verify installation:
     ```bash
     java -version
     javac -version
     ```
 
2. **Install Maven**
   - Download from [Apache Maven](https://maven.apache.org/download.cgi)
   - Or install via package manager:
     ```bash
     # macOS (Homebrew)
     brew install maven
     
     # Ubuntu/Debian
     sudo apt install maven
     
     # Windows (Chocolatey)
     choco install maven
     ```
   - Verify installation:
     ```bash
     mvn -version
     ```
 
### Build Steps
 
1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd group26
   ```
 
2. **Build the project with Maven**
   ```bash
   mvn clean package
   ```
   This will:
   - Download all dependencies
   - Compile the source code
   - Run any tests
   - Package the application into a JAR file
 
3. **Locate the built JAR**
   
   After a successful build, the JAR file will be located at:
   ```
   target/typetris-1.0.jar
   ```
   (or similar, depending on your pom.xml configuration)
 
### Build Troubleshooting
 
- **Maven not found**: Ensure Maven's `bin` directory is in your system PATH
- **Java version mismatch**: Ensure `JAVA_HOME` points to JDK 17+
- **Dependency errors**: Run `mvn clean install -U` to force update dependencies
 
---
 
## Running the Application
 
### Option 1: Run with Maven (Development)
 
```bash
mvn exec:java -Dexec.mainClass="typetris.App"
```
 
### Option 2: Run the JAR (Production)
 
```bash
java -jar target/typetris-1.0.jar
```
 
### Option 3: Run from IDE
 
1. Import the project as a Maven project
2. Navigate to `src/main/java/typetris/App.java`
3. Run the `main` method
 
### First Run
 
On first launch, the application will:
1. Create a `data/` directory in the current working directory
2. Initialize the SQLite database at `data/type-tris.db`
3. Display the login/registration screen
 
---
 
## User Guide
 
### Getting Started
 
1. **Launch the application**
2. **Register a new account** or **Login** with existing credentials
3. **Select difficulty** from the player menu:
   - **Easy**: Slower fall speed, shorter words (3-4 letters)
   - **Medium**: Moderate speed, medium words (5 letters)
   - **Hard**: Fast speed, longer words (6-7 letters)
4. **Select sub-level** (1, 2, or 3) - higher sub-levels are faster
 
### Gameplay
 
1. **Typing Phase**
   - A word-shaped block falls from the top
   - Type the word shown on the block correctly
   - The block "unlocks" when you complete the word
   - Status shows: "Type the word"
 
2. **Placement Phase**
   - Once unlocked, use controls to position the block:
     - **← Left Arrow**: Move block left
     - **→ Right Arrow**: Move block right
     - **↑ Up Arrow**: Rotate block clockwise
     - **↓ Down Arrow**: Speed up falling / soft drop
     - **Spacebar**: Hard drop (instant placement)
   - Status shows: "Place the block"
 
3. **Scoring**
   - Clear rows by filling them completely
   - Build streaks for combo multipliers
   - Faster typing = higher WPM bonus
 
4. **Power-ups** (Press 1, 2, or 3 to activate)
   - **Freeze (1)**: Temporarily stops block falling
   - **Easy (2)**: Reduces word difficulty for next block
   - **Blast (3)**: Clears bottom rows
 
5. **Game Over**
   - Lose a life when a block locks above the visible grid
   - Game ends when all lives are lost
 
### HUD Elements
 
- **Score**: Current game score
- **Best**: Your best score for this difficulty/level
- **WPM**: Words per minute (current and overall)
- **Accuracy**: Typing accuracy percentage
- **Lines**: Total lines cleared
- **Streak**: Current line-clear streak
- **Multiplier**: Score multiplier from streaks
- **Level**: Current progression level
- **Lives**: Remaining lives (♥ symbols)
 
### Navigation
 
- **Pause**: Press `Esc` during gameplay
- **Resume**: Click "Resume" on pause menu
- **Quit**: Click "Quit" to return to menu (progress not saved)
 
---
 
## Account Information
 
### Default Test Accounts
 
| Username | Password | Notes |
|----------|----------|-------|
| `admin` | `admin123` | Administrator account with parental controls access |
| `testuser` | `password` | Standard player account for testing |
 
### Creating a New Account
 
1. Click "Register" on the login screen
2. Enter a unique username
3. Enter a password
4. Confirm the password
5. Click "Create Account"
 
### Password Requirements
 
- Minimum 8 characters
- No special requirements (alphanumeric accepted)
 
---
 
## Parental Controls
 
### Accessing Parental Controls
 
1. Login with an Parental/Teacher account with code: 2212
2. Navigate to the **Admin Panel** from the player menu
3. Admin features available:
   - **View all player statistics**
   - **Reset global leaderboard**
   - **Delete/create accounts**
 
### Admin Features
 
| Feature | Description |
|---------|-------------|
| View Statistics | See all registered players and their stats |
| Reset Leaderboard | Clear all high scores from the database |
| Delete/Create accounts | Delete existing account from database or create new accounts |
 
### Project Structure
 
```
group26/
├── src/main/java/typetris/
│   ├── App.java                 # Application entry point
│   ├── backend/
│   │   ├── AuthRepository.java  # Database and authentication
│   │   ├── CompletedRunStats.java
│   │   └── PlayerAccount.java
│   ├── gameplay/
│   │   ├── GameplayModel.java   # Core game logic
│   │   ├── GameplayPanel.java   # Game UI and controls
│   │   └── GameplayAudio.java   # Sound effects
│   └── ui/
│       ├── UserInterface.java   # Main application shell
│       ├── TypeTrisTheme.java   # Visual styling
│       ├── ClientPrefs.java     # Local preferences storage
│       ├── Screens.java         # Screen navigation
│       └── ...
├── src/main/resources/typetris/
│   └── wordlist.txt             # Word dictionary
├── data/
│   └── type-tris.db             # SQLite database (created at runtime)
├── pom.xml                      # Maven build configuration
└── README.md                    # This file
```
 
### Database Schema
 
The SQLite database contains tables for:
- User accounts (username, password hash, admin flag)
- Game statistics (scores, WPM, accuracy per session)
- Leaderboard entries
 
### Known Issues / Limitations
 
- Sound may not work on all systems (requires Java audio support)
- Window is fixed size and not resizable
- Single-player only (no multiplayer support)
 
### Testing the Application
 
1. Build and run following the instructions above
2. Create a test account or use provided credentials
3. Play through Easy → Medium → Hard to test progression
4. Verify high scores appear on the leaderboard
5. Test admin features with the admin account
 
### Contact
 
**CS2212 Group 26** - Western University
 
---
