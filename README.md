# Ultimate Task Master

A RuneLite plugin for tracking and managing in-game tasks and goals.

## Building

This project uses **Maven** and targets **JDK 11** (Eclipse Temurin recommended).

### IntelliJ IDEA Setup

1. **Open the project:** `File > Open…` → select the `Ultimate-Task-Master` folder
2. **Trust the project** when prompted
3. **Configure JDK:** `File > Project Structure > Project` → Download JDK 11 (Eclipse Temurin / AdoptOpenJDK HotSpot)
4. **Set language level** to `11`
5. IntelliJ will auto-detect the `pom.xml` and import Maven dependencies

### Running the Plugin

Run `UltimateTaskMasterPluginTest.main()` in `src/test/java` — this boots the full RuneLite client with the plugin loaded.

### Building from CLI

```bash
mvn clean package
```

## License

See [LICENSE](LICENSE) for details.
