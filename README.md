RepDev
======

RepDev IDE for Credit Unions

The RepDev IDE allows for easy and convenient editing of RepGen programs for Symitar based Credit Unions. 

Features:
 - Connects directly to your Symitar host with Telnet or SSH
 - Connects to multiple Symitar Servers at the same time
 - Connect to multiple SYMs at the same time
 - Run reports directly from the edit window
 - IntelliSense/Auto Complete
 - Syntax highlighting
 - Code snippets and advanced editing features
 - Create/Manage multiple projects to group all of the RepGens, LetterFiles, HelpFiles and DataFiles together
 - Date archive multiple files at the same time to quickly back them up
 - Drag and drop multiple files from Test to Production at the same time
 - Pleasant graphical theme
 - Supports EASE Client Menu Selections
 - Supports Oracle/Sun Java as well as Open JDK
 - Supports RepDev Single Sign-On to Servers and SYMs
 - Facilitates your In-House Source Control Solution

The latest versions can be download here:
<a href="https://github.com/jakepoz/RepDev-downloads">RepDev Downloads</a> (scroll toward the bottom)

Documentation is available for download here:
<a href="https://github.com/jakepoz/RepDev-downloads/raw/master/RepDev_Guide.pdf">RepDev Guide</a>

Build
-----

RepDev now has a primary Maven build path.

Requirements:
 - Java 21
 - Maven 3.9+
 - 64-bit OS/JRE

Common commands:
 - `mvn -q -DskipTests compile`
 - `mvn -q -DskipTests package`

The packaged app layout is written to `target/app/`.
On Windows you can launch either:
 - the repo-root `start.bat` after packaging, which will prefer `target/app/`
 - the copied `start.bat` inside `target/app/`
  - `package-windows.bat` to build the app layout and, if Launch4j is installed, produce `target/windows/repdev.exe`

Runtime SVG icon support uses SWT SVG plus `jsvg`.
Runtime action icons are now SVG-only. Branding assets such as the app icon and About logo remain bitmap-based.
