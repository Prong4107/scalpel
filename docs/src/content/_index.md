---
title: "Introduction"
layout: single
menu:
    overview:
        weight: 1
---

# Introduction

Scalpel is a Burp extension for intercepting and modifying HTTP trafic using simple Python scripting.

# Index

-   [Installation]({{< relref "overview-installation" >}})
-   [Usage]({{< relref "overview-usage" >}})
-   [FAQ]({{< relref "overview-faq" >}})
-   [Technical documentation]({{< relref "addons-api" >}})
-   [Example use-case]({{< relref "tute-aes" >}})
-   [How scalpel works]({{< relref "concepts-howscalpelworks" >}})

## Features

-   Make scripted changes to HTTP traffic using Python.
-   Interactively edit plaintext decoded and encoded by a custom script.

[GitHub repository](https://code.corp.lexfo.fr/pentester/scalpel).
^TODO: set github repo

## Use-cases

-   [Editing encoded requests/responses]({{< relref "addons-examples#GZIP" >}})
-   [Decrypting custom encryption]({{< relref "tute-aes" >}})

> Note: One might think existing Burp extensions like piper can handle such cases, but it is actually too limited, for example, when intercepting a response, piper cannot get informations form the originating request, which is required in the above case. In general, Scalpel allows you to work around cases more complex than other Burp extensions like Piper or Hackvertor.
