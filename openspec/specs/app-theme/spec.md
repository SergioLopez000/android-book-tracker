## Capability: app-theme

### Purpose
App-wide dark Material3 theme applied at the composition root.

### Requirement: Apply Custom Dark Color Scheme
The system MUST apply a custom dark Material3 `ColorScheme` and `Typography` at the composition root; no screen MUST fall back to stock `MaterialTheme` defaults or a light scheme.

#### Scenario: App launches under dark theme
- GIVEN the app is launched
- WHEN the library screen renders
- THEN every surface, text and control uses the custom dark color scheme

#### Scenario: Theme consistent across screens
- GIVEN the user navigates from list to edit screen
- WHEN the edit screen renders
- THEN it uses the same dark color scheme, not Compose defaults
