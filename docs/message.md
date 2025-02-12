---
layout: default
title: Message API
nav_order: 8
---

# Message API

The Message API aims to cover the Messages part of the [REST API documentation](https://developers.symphony.com/restapi/main/messages).
More precisely:
* [Get a message](https://developers.symphony.com/restapi/main/messages/get-message-v1)
* [Get messages](hhttps://developers.symphony.com/restapi/main/messages/messages-v4)
* [Search messages](https://developers.symphony.com/restapi/main/messages/message-search-post)
* [Get message by multiple query params](https://developers.symphony.com/restapi/main/messages/message-search-get)
* [Send message](https://developers.symphony.com/restapi/main/messages/create-message-v4)
* [Update message](https://developers.symphony.com/restapi/main/messages/update-message-v4)
* [Import messages](https://developers.symphony.com/restapi/main/messages/import-message-v4)
* [Get attachment](hhttps://developers.symphony.com/restapi/main/messages/attachment)
* [List attachments](https://developers.symphony.com/restapi/main/messages/list-attachments)
* [Get allowed attachment types](https://developers.symphony.com/restapi/main/messages/attachment-types)
* [Suppress message](hhttps://developers.symphony.com/restapi/main/messages/suppress-message)
* [Get message status](https://developers.symphony.com/restapi/main/messages/message-status)
* [Get message receipts](https://developers.symphony.com/restapi/main/messages/list-message-receipts)
* [Get message relationships](https://developers.symphony.com/restapi/main/messages/message-metadata-relationship)

## How to use
The central component for the Message API is the `MessageService`.
It exposes all the services mentioned above and is accessible from the `SymphonyBdk` object by calling the `messages()` method:
```java
@Slf4j
public class Example {
  public static final String STREAM_ID = "gXFV8vN37dNqjojYS_y2wX___o2KxfmUdA";

  public static void main(String[] args) throws Exception {
    // Create BDK entry point
    final SymphonyBdk bdk = new SymphonyBdk(loadFromClasspath("/config.yaml"));
    // send a regular message
    final V4Message regularMessage = bdk.message().send(STREAM_ID, Message.builder().content("Hello, World!").build());
    log.info("Message sent, id: " + regularMessage.getMessageId());
  }
}
```
> `Message.builder().content("Hello, World!").build()` will automatically prefix and suffix content with `"<messageML>"` and `"</messageML>"`.
> Therefore, the actual `Message.getContent()` result will be `"<messageML>Hello, World!</messageML>"`

> `PresentationMLParser.getTextContent(message.getMessage())` can be used on incoming messages to extract the message content
> stripped of all tags.
## Using templates
The `Message.Builder` also allows you to build a message from a template. So far, the BDK supports two different template
engine implementations:
- [FreeMarker](https://freemarker.apache.org/) (through dependency `org.finos.symphony.bdk:symphony-bdk-template-freemarker`)
- [Handlebars](https://github.com/jknack/handlebars.java) (through dependency `org.finos.symphony.bdk:symphony-bdk-template-handlebars`)

### How to send a message built from a template
> In the code examples below, we will assume that FreeMarker as been selected as template engine implementation.
> See [how to select the template engine implementation](#select-your-template-engine-implementation).

#### Template file

First you need to define your message template file. Here `src/main/resources/templates/simple.ftl`:
```
<messageML>Hello, ${name}!</messageML>
```
you will be able to use it when sending message:
```java
public class Example {
  public static final String STREAM_ID = "gXFV8vN37dNqjojYS_y2wX___o2KxfmUdA";

  public static void main(String[] args) {
    final SymphonyBdk bdk = new SymphonyBdk(loadFromClasspath("/config.yaml"));
    final Template template = bdk.messages().templates().newTemplateFromClasspath("/templates/simple.ftl");

    final Message message = Message.builder().template(template, Collections.singletonMap("name", "User")).build();
    final V4Message regularMessage = bdk.message().send(streamId, message);
  }
}
```
The above will send the message `<messageML>Hello, User!</messageML>` as expected.

> Please note that the `MessageService` will try fetch template from different locations ordered by:
> 1. classpath
> 2. file system

It is also possible to get direct access to the `TemplateEngine` through the `MessageService`:
```java
@Slf4j
public class Example {

  public static void main(String[] args) {
    final SymphonyBdk bdk = new SymphonyBdk(loadFromClasspath("/config.yaml"));

    // load template from classpath location
    final Template template = bdk.messages().templates().newTemplateFromClasspath("/complex-message.ftl");

    // process template with some vars and retrieve content
    // any POJO can also be processed by the template
    final String content = template.process(Collections.singletonMap("name", "Freemarker"));

    // display processed template content
    log.info(content);
  }
}
```

#### Select your template engine implementation
Developers are free to select the underlying template engine implementation. This can be done importing the right
dependency in your classpath.

With [Maven](./getting-started.html#maven-based-project):
```xml
<dependencies>
        <dependency>
            <groupId>org.finos.symphony.bdk</groupId>
            <artifactId>symphony-bdk-template-freemarker</artifactId>
            <scope>runtime</scope>
        </dependency>
        <!-- or -->
        <dependency>
            <groupId>org.finos.symphony.bdk</groupId>
            <artifactId>symphony-bdk-template-handlebars</artifactId>
            <scope>runtime</scope>
        </dependency>
</dependencies>
```
With [Gradle](./getting-started.html#gradle-based-project):
```groovy
dependencies {
    runtimeOnly 'org.finos.symphony.bdk:symphony-bdk-template-freemarker'
    // or
    runtimeOnly 'org.finos.symphony.bdk:symphony-bdk-template-handlebars'
}
```
> :warning: If multiple implementations found in classpath, an exception is throw in order to help you to define which one
> your project really needs to use.


#### Inline template string

Simple template can be created as an inline template. Take the same example above, simply pass the template string to the function, such like

```java
public class Example {

  public static void main(String[] args) {
      final SymphonyBdk bdk = new SymphonyBdk(loadFromClasspath("/config.yaml"));
      final Template template = bdk.messages().templates().newTemplateFromString("<messageML>Hello, ${name}!</messageML>");
      final String content = template.process(Collections.singletonMap("name", "Freemarker"));
      log.info(content);
  }
}
```

----
[Home :house:](./index.html)
