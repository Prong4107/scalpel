---
title: "Installation"
menu: "overview"
menu:
    overview:
        weight: 2
---

# Installation

## Requirements

-   OpenJDK >= `17`
-   Python >= `3.10`
-   pip
-   venv

## Step-by-step instructions

-   Download the latest [JAR release](https://REMOVED/scalpel/-/releases). {{< figure src="/screenshots/release.png" >}}

-   Import the `.jar` to Burp. {{< figure src="/screenshots/import.png" >}}
-   Wait for the dependencies to install. {{< figure src="/screenshots/wait.png" >}}
-   Once Scalpel is properly initialized, you should get the following: {{< figure src="/screenshots/init.png" >}}
-   If the installation was successful, a `Scalpel` tab should show in the Request/Response editor as follows: {{< figure src="/screenshots/tabs.png" >}}
-   And also a `Scalpel` tab for configuration to install additional packages via terminal. {{< figure src="/screenshots/terminal.png" >}}

Scalpel is now properly installed and initialized!

## What's next

-   Check the [Usage]({{< relref "overview-usage" >}}) page to learn how to use the tool.
-   Read this [tutorial]({{< relref "tute-aes" >}}) to see Scalpel in a real use case context.
