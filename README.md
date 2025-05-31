# BlueRemote

BlueRemote is an Android app that allows you to control a remote device (such as a robot or car) via Bluetooth using simple character signals. The app features a modern UI with light/dark mode toggle, a user manual, and customizable control buttons.

## Features

- **Bluetooth Scanning & Connection**: Scan for nearby Bluetooth devices and connect easily.
- **Control Panel**: Send character signals (F, B, L, R, S, +, -) to your device using intuitive buttons.
- **Reserved Buttons**: Set custom character signals for two reserved buttons.
- **Message Display**: View sent/received signals with timestamps.
- **Light/Dark Mode**: Toggle between light and dark themes. The toggle icon updates (sun/moon) based on the current mode.
- **User Manual**: Access a built-in manual explaining all features and button values.

## Button Signal Mapping

| Button      | Signal Sent |
|-------------|-------------|
| Forward     | F           |
| Backward    | B           |
| Left        | L           |
| Right       | R           |
| Stop        | S           |
| Plus        | +           |
| Minus       | -           |
| Reserved 1  | Custom      |
| Reserved 2  | Custom      |

## How to Use

1. **Scan & Connect**: Tap the Scan button to find and connect to your Bluetooth device.
2. **Send Signals**: Use the control buttons to send character signals to your device.
3. **Reserved Buttons**: Tap to set a custom character. Long-press to send the custom signal.
4. **Theme Toggle**: Tap the sun/moon icon to switch between light and dark mode.
5. **User Manual**: Tap the info icon (top right) for detailed instructions and button mapping.

## Screenshots

> Add screenshots here to showcase the UI in both light and dark mode.

## Requirements

- Android 6.0 (API 23) or higher
- Bluetooth-enabled device

## Building & Running

1. Clone this repository:
   ```sh
   git clone https://github.com/thehav0k/Blue-Remote.git
   ```
2. Open in Android Studio.
3. Build and run on your device or emulator.

## Customization

- **Button Signals**: You can change the character sent by reserved buttons.
- **Theme**: The app remembers your last selected theme.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.

## Credits

- Sun and Moon icons: [Material Icons](https://fonts.google.com/icons)
- Built with Android, Kotlin, and Material Design

---

For questions or contributions, please open an issue or pull request on GitHub.

