package com.symphony.bdk.examples.spring;

import com.symphony.bdk.core.activity.command.CommandContext;
import com.symphony.bdk.core.activity.parsing.Cashtag;
import com.symphony.bdk.core.activity.parsing.Hashtag;
import com.symphony.bdk.core.activity.parsing.Mention;
import com.symphony.bdk.core.service.message.MessageService;
import com.symphony.bdk.spring.annotation.Slash;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * This is to show how to use @Slash annotations with arguments
 */
@Component
@Slf4j
public class EchoActivity {

  @Autowired
  private MessageService messageService;

  @Slash("/echo {argument}")
  public void echo(CommandContext context, String argument) {
    Instant timestamp = Instant.ofEpochMilli(context.getEventTimestamp());
    log.info("{}", timestamp);
    this.messageService.send(context.getStreamId(), String.format("Received argument: %s at %s", argument, timestamp));
  }

  @Slash("/echo {@mention}")
  public void echo(CommandContext context, Mention mention) {
    this.messageService.send(context.getStreamId(),
        "Received mention: " + mention.getUserDisplayName() + " of id: " + mention.getUserId());
  }

  @Slash("/echo {#hashtag}")
  public void echo(CommandContext context, Hashtag hashtag) {
    this.messageService.send(context.getStreamId(), "Received hashtag: " + hashtag.getValue());
  }

  @Slash("/echo {$cashtag}")
  public void echo(CommandContext context, Cashtag cashtag) {
    this.messageService.send(context.getStreamId(), "Received cashtag: " + cashtag.getValue());
  }

  @Slash("/echo {argument} {$cashtag}")
  public void echo(CommandContext context, String argument, Cashtag cashtag) {
    this.messageService.send(context.getStreamId(),
        "Received argument: " + argument + " and cashtag: " + cashtag.getValue());
  }

}
