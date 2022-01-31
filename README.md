# apm-java-socket-plugin

An Elastic APM agent plugin that creates an exit span when `java.net.Socket#connect` is called and no exit span exist yet.

## Supported Versions

| Plugin | Elastic APM Agent |
| :--- |:------------------|
| 1.0+ | 1.27.0+           |

## Installation

Set the [`plugins_dir`](https://www.elastic.co/guide/en/apm/agent/java/current/config-core.html#config-plugins-dir) agent configuration option and copy the plugin to specified directory.
