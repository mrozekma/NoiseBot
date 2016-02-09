## User documentation

As long as the **Help** module is loaded, NoiseBot modules are self-documenting. Run `.help` to get a list of loaded modules. Run `.help modulename` to get an explanation of what that module is for and some example commands. The **Core** module (which is always loaded) contains commands for loading/unloading other modules, as long as the bot owner allows it; use `.help Core` for more information on those commands.

## Owner documentation

You probably don't care about this, since as far as I know all existent instances of NoiseBot are run by me. I'm a little concerned at the ratio of developers to users on this project, come to think of it.

### Configuration

Copy [`config.sample`](/config.sample) to `config` and fill it in. If you only need one connection, name it `default` so it will be chosen automatically. The individual fields are mostly documented in the sample file, although the per-module keys are not, since I'm unlikely to remember to keep them up to date. Run `grep @Configurable NoiseBot/src/modules/*.java` to find a list of per-module configuration keys that are hopefully self-explanatory.

For the owner block, you probably only want to set the `account` entry (which corresponds to your Nickserv account on IRC, and your account name on Slack). If you're not identified on IRC you can use a combination of the other fields, but that's less reliable. You can make sure you've got it right by running the `.owner?` command.

### Building and running

Build with `make`, and connect to the the default connection with `make run`. If you want to run a specific set of connections, it's probably best to either change the `run` target in the Makefile or add a new one to do it, rather than running the bot directly. You can copy how the `test` target does this, changing `conn=test` to whatever list of targets you need:

```
.PHONY: test
test: conn=test
test: run
```

The `.restart` command is implemented by exiting with error code 2, which the Makefile catches, so if you run NoiseBot directly this functionality is lost unless you handle it yourself.

### Updating

NoiseBot supports automatic updating, but this requires a push from Github. Contact me somehow if you want this setup; I'll need a URL that points to the host your instance is running on, and what events you're interested in hearing about:

| Event | Usage |
| --- | --- |
| Push | If you want the bot to automatically pull new commits |
| Issues | If you want the bot to output changes to the issue list |

You'll need to make sure TCP/41933 is open on that host, and set the secret I send you in the `github-webhook-secret` entry of the configuration file. If you don't use automatic updating, you can still pull updates at any time with the `.sync` command (owner-only).

## Developer documentation

At last, the relevant part. This only covers module editing, since that's probably all anybody but me needs to care about anyway.

### Commit access

1. Talk to me for an account on `noisebot.mrozekma.com`
2. Clone from `noisebot@noisebot.mrozekma.com:~/NoiseBot`
3. Make changes
4. Push to the remote master branch

The push is automatically rejected if it fails to build. Otherwise it should appear on Github immediately, and bots configured to automatically update will show it in a few seconds.

### Structure of a module

Modules are classes in the `modules` package that extend `NoiseModule`. Only a couple methods are strictly required, all for implementing the internal help system, so this is a working module:

```java
package modules;
import main.NoiseModule;

public class Demo extends NoiseModule {
	@Override public String getFriendlyName() {return "Demo";}
	@Override public String getDescription() {return "A demonstration module";}
	@Override public String[] getExamples() {return new String[0];}
}
```

It doesn't have any commands, but you can load/unload it and it will show up in help. There are other methods you can override if necessary, although you must remember to call the parent implementation with `super.foo(...)`:

* `public void init(NoiseBot) throws ModuleInitException` -- Called when the module is loaded; startup tasks should be done here instead of in a constructor. If a `ModuleInitException` is thrown, the load fails and the module is unavailable.
* `public void unload()` -- Called when the module is unloaded. Any threads or timers running in the background should be canceled here.
* `public void setConfig(Map<String, Object>) throws ModuleInitException` -- Called to set per-module configuration. This is a convenient place to do initialization that depends on configuration keys, since those values aren't available at init-time. Call `super.setConfig(...)`, and then use any of your `@Configurable` fields as needed.
* `protected Map<String, Style> styles()` -- Explained in the [**Style strings**](#style-strings) section.
* `protected void joined(String)` -- Called when the specified nick has joined the channel this bot is in.
* `protected void left(String)` -- Called when the specified nick has parted the channel this bot is in.

### Commands

Commands are methods that are triggered when a user sends a message that matches their pattern. Command methods are annotated with `@Command`, which takes several possible parameters:

| Parameter | Description | Default |
| --- | --- | --- |
| `value` | The regular expression that triggers this command. This is referred to as the `pattern` elsewhere in this documentation; it's called `value` only because Java requires it for the default annotations parameter | None (mandatory) |
| `allowPM` | A flag indicating if this command can be triggered by private message | True |
| `caseSensitive` | A flag indicating if this command's pattern is case-sensitive | True |

Java's annotation syntax allows you to leave off the `value` key as long as it is the only argument, which is usually the case. For example:

```java
@Command("\\.demo ([0-9]+)")
```

This command matches `.demo 42`, but not any of the following:

* `.demo`
* `.demo foobar`
* `.demo 42 foobar`
* `foobar .demo 42`
* `.Demo 42`

The last could be allowed by making the pattern case-insensitive:

```java
@Command(value = "\\.demo ([0-9]+)", caseSensitive = false)
```

If a user sends a message matching your command, NoiseBot will invoke the accompanying method. The method's first parameter must be of type `main.CommandContext`, a class that contains information about the triggering message, who sent it, and [whence it came](https://www.youtube.com/watch?v=TrJJ6ncp1fc). Further parameters must match the groups in the pattern. There is special support for integers, but all other groups must be accepted as Strings and parsed further within your method if necessary. In this case:

```java
@Command("\\.demo ([0-9]+)")
public void demoCommand(CommandContext ctx, int arg) {}
```

#### Old-style commands

Old-style commands are commands that do not return data (i.e. they have return type `void`, like the example above). These commands generally send something back to the channel to interact with users. The best way to do with is with the `CommandContext::respond()` method, called on the `CommandContext` instance passed as the first argument to your command method. This is a printf-style method. The actual format string is augmented, but for the moment we can ignore that. Example usage:

```java
@Command("\\.demo ([0-9]+)")
public void demoCommand(CommandContext ctx, int arg) {
    ctx.respond("You sent the number %d!", arg);
}
```

This method of responding is preferred because it ensures that the message goes to the right place -- either the channel, or a private message to the user who sent the command. You also have direct access to the NoiseBot that owns this module at `this.bot`, and so can call `this.bot.sendMessage(...)`, but should only do so if you're sure the message should go to the channel in all circumstances.

#### New-style commands

New-style commands return structured data instead of sending human-friendly text to the server. These methods have a return type of `JSONObject` and are permitted to throw `JSONException`. For example:

```java
@Command("\\.demo ([0-9]+)")
public JSONObject demoCommand(CommandContext ctx, int arg) throws JSONException {
    return new JSONObject().put("number", arg);
}
```

If you create such a command and run it, you will see that even though no messages were sent to the server, the user will get a response showing the JSON representation of the returned data:

```javascript
{"number": 42}
```

This is facilitated by a **View**, explained in the [**Views**](#views) section below.

### Views

Analogous to a view in MVC, a NoiseBot view takes the structured data coming out of a command and turns it into human-friendly text that can be send to a channel or user. Commands and views are split up for two main reasons:

* Commands that return structured data can be easily composed. One command can invoke another command and use its output, and eventually it is planned that users will be able to issue meta-commands that give arguments to one command by pulling data out of the output from another.
* Different methods of displaying the data can be employed for different protocols. IRC supports colored text while Slack supports emoji and attachments; it is often desirable to take advantage of these different features without breaking functionality on the protocols that don't have them :+1:.

To facilitate the second point, views can specify which protocol(s) and command method(s) they apply to. The view to use for a given piece of data is selected with a CSS-like specificity criteria. In decreasing order, NoiseBot will use:

* A view that specifically whitelists the protocol and command method
* A view that specifically whitelists the command method
* A view that specifically whitelists the protocol
* A generic view that doesn't specify protocol nor command
* A default view provided by the framework

In the event of a tie at a given level, the view specified earliest in the module is used. A view that doesn't specify a protocol or command method is assumed to support all of them, but a view that does specify those values will never be used in any other situation. For example, a view that doesn't specify a protocol will be valid for any protocol, but a view that specifies support for IRC will never be used if the bot is connected to Slack. For this reason, a view that only outputs plain text should generally not specify any protocol, since it will work on all of them.

The last option in the list is a *default view*. Currently there is just one, which outputs the data in JSON format as mentioned above in [**New-style commands**](#new-style-commands). This is probably never what you want, but it does ensure that the command's output isn't silently discarded.

Views, like commands, are annotated methods in the module. The possible parameters are:

| Parameter | Description | Default |
| --- | --- | --- |
| `value` | The list of protocols this view can be used on (`main.Protocol` is an enum) | All protocols are valid |
| `method` | The list of command methods this view can be used on, by name. | All methods are valid |

Both of these parameters are lists, which in Java annotation syntax are wrapped in curly braces, but these are optional if a single value is passed. For example:

```java
@View
@View(Protocol.IRC)
@View(method = {"foo", "bar"})
@View(value = {Protocol.IRC, Protocol.Slack}, method = "baz")
```

The annotated method must take a ViewContext argument (analogous to the CommandContext passed to commands), plus a `JSONObject` for the data coming out of the command, and return void. Like the command, it's permitted to throw `JSONException`. It can then send messages just like an old-style command would. For example:

```java
@View(method = "demoCommand")
public void plainView(ViewContext ctx, JSONObject data) throws JSONException {
    ctx.respond("You sent the number %d!", data.getInt("number"));
}
```

#### Indicating errors

For new-style commands, the `error` key is handled specially by the framework. If the data returned from a command includes an `error` key, the string stored there is formatted with the "error" style and sent back to the server. The module's views are not invoked in this circumstance.

### Configurable fields

You can add configuration keys specific to your module by annotating fields with `@Configurable`. The possible parameters are:

| Parameter | Description | Default |
| --- | --- | --- |
| `value` | The name of the key in the configuration file | None (mandatory) |
| `required` | A flag indicating if this key must be in the configuration file for the module to load | True |

These fields are filled in during your module's `setConfig()` method (in the parent implementation, so if you override it and don't call `super.setConfig()` this will no longer work). The fields can be any primitive type, as well as `File`; in this last case, the framework will expect the key to be a filename and will ensure that the file exists.

### Constructing messages

The process of going from module data to actual messages sent to the server is comically complex and involves at least 4 passes. I do my best to shield module authors from this insanity, but you do need to somewhat understand the first pass, message building. The `MessageBuilder` class is responsible for this step. You obtain a `MessageBuilder` either from a `CommandContext`/`ViewContext`:

```java
MessageBuilder builder = ctx.buildResponse();
MessageBuilder builder2 = ctx.buildActionResponse();
```

Or from a `NoiseBot` (the bot that owns your module is accessible via `this.bot`):

```java
MessageBuilder builder = this.bot.buildMessage();
MessageBuilder builder2 = this.bot.buildMessageTo(username);
MessageBuilder builder3 = this.bot.editMessage(replacing);
MessageBuilder builder4 = this.bot.buildAction();
MessageBuilder builder5 = this.bot.buildActionTo(username);
MessageBuilder builder6 = this.bot.buildNotice();
```

As discussed above, in most cases you want to use the methods on a context, as they handle making sure the response goes to the same place the original message came from (either a channel or a private message).

You build up a message by one or more calls to `MessageBuilder::add(String fmt, Object[] args)`, and send it with `MessageBuilder::send()`. Pieces of a message are concatenated, so these are functionally equivalent:

```java
builder.add(fmt1, args1).add(fmt2, args2).send();
builder.add(fmt1 + fmt2, ArrayUtils.addAll(args1, args2)).send();
```

Both the contexts and `NoiseBot` have helper methods if you only need to add one piece to a builder. These helpers take the format string and arguments (variadically), and handle making the builder, adding to it, and sending:

```java
ctx.respond(fmt, arg1, arg2, ...);
this.bot.sendMessage(fmt, arg1, arg2, ...);
this.bot.sendMessageTo(username, fmt, arg1, arg2, ...);
this.bot.sendAction(fmt, arg1, arg2, ...);
this.bot.sendActionTo(username, fmt, arg1, arg2, ...);
this.bot.sendNotice(fmt, arg1, arg2, ...);
```

`MessageBuilder::add` takes a single array of arguments because of Java's brain-damaged handling of variadics, a lesson I apparently forgot in my zeal to make the helper methods easy to use. Like Python, Java's designers wanted variadic methods to be callable with either multiple individual arguments, or an array containing the arguments. A useful feature. Unlike Python, it did not occur to them to have syntax indicating which of the two things you are currently doing. Which means that this:

```java
ctx.respond(fmt, new Object[] {"a", "b", "c"});
```

is ambiguous. Are you passing the `Object[]` as the single argument to the method, or are you saying the method should take the three values contained in the `Object[]` as arguments? javac will emit a warning and then assume you meant the latter -- to get the former behavior, you must cast the argument to a non-array type:

```java
ctx.respond(fmt, (Object)new Object[] {"a", "b", "c"});
```

Thanks, Java. Fortunately, this only comes up when you pass a single argument and that argument is an array type.

### Style strings

In the olden times, we handled IRC styles, if they can even be called that, with in-band magic bytes. Green text was made by concatenating `org.jibble.pircbot.Colors.GREEN` into the string itself. Now the format string contains style information, and green text is made by including the `#green` style tag. For example:

```java
ctx.respond("This text is normal, while #green this text is green");
```

Note that the style tag is followed by a space; this is not included in the final output.

There are a number of built-in styles, listed at the top of the [`Style`](/src/main/Style.java) class. You can compose styles with the `Style::update()` method. For example, to get red bold text:

```java
Style redBold = Style.RED.update("bold");
```

To make your custom styles available within format strings, override the `NoiseModule::styles()` method and include them in the returned map:

```java
@Override public Map<String, Style> styles() {
    return new HashMap<String, Style>() {{
        put("redbold", Style.RED.update("bold"));
        // Now "#redbold" will work in style strings
    }};
}
```

In addition to standard Java [format string syntax](https://docs.oracle.com/javase/8/docs/api/java/util/Formatter.html#syntax), the following syntax is supported:

| Syntax | Description | Arguments consumed |
| --- | --- | --- |
| `#foo` | Switch to style "foo" for any succeeding text. "foo" must be defined either in this module's `styles()` map, or in the `Style` class | 0 |
| `##` | An escaped `#` | 0 |
| `# ` | Switch to a style passed as an argument. The argument can be either a `Style` instance, or a `String` containing the style's name | 1 |
| `%%` | An escaped `%` | 0 |
| `%(#foo)s` | A `%s` that is styled as `#foo`. Equivalent to `#foo %s#previous_style` | 1 |
| `%#s` | A `%s` that is styled with a style passed as an argument. Equivalent to `# %s#previous_style`. Note that the style is passed before the argument for the format specifier | 2 |
| `#([sep] fmt)` | A multi-part message using the separator `sep`, [explained below](#multi-part-messages) | Varies |
| `#(fmt)` | A multi-part message with the default separator, a space | Varies |

### Multi-part messages

Multi-part messages are messages with a (generally varying) number of pieces that need to be concatenated together. For example, the **Score** module shows entries of the form `username: score` for each recorded score, separated by commas. The **Weather** module shows entries of the form `[city: condition, temperature]`, separated by spaces. NoiseBot provides special support for these messages because it allows the framework to wrap overlong messages between entries, instead of cutting off an entry in the middle.

The style syntax is `#([sep] fmt)`. `sep` is the string that is used to separate each group; if `[sep]` is omitted, a space is used. `fmt` is the format string for an individual entry. We're going to focus on the real **Score** module output as a demonstration; the complete format string used by that module is `#([, ] %s: %#d)`. This is a multi-part group with entries separated by `, `, and each entry formatted by `%s: %#d`. As explained in the style syntax table, `%#d` is a `%d` that's styled by an argument, since negative scores are colored red and non-negative green. This means that each entry needs three arguments: the username (`String`), the score style (`Style` or `String`), and the score (`int`).

All arguments for a multi-part group are passed in a single array to the formatter function. For example, the format string `%d #([, ] %s: %s) %f` should be accompanied by three arguments: an `int`, a `String[]` containing two strings per entry in the multi-part group, and finally a `float`. More concretely:

```java
ctx.respond("%d %([, ] %s: %s) %f", 42, new String[] {"foo", "bar", "baz", "qux"}, 100.0);
// Resulting output: 42 foo: bar, baz: qux 100.0
```

The multi-part group format string can include all the same style strings as the parent string, but cannot contain another multi-part group -- groups do not nest. In the case of **Score**, the code might be something like this:

```java
@Override public Map<String, Style> styles() {
    return new HashMap<String, Style>() {{
        put("positive", Style.GREEN);
        put("negative", Style.RED);
    }};
}

@Command("\\.scores")
public void scores(CommandContext ctx) {
    final List<Object> args = new LinkedList<>();
    for(Map.Entry<String, Integer> score : this.scores) {
        args.add(score.getKey()); // username
        args.add(score.getValue() >= 0 ? "positive" : "negative");
        args.add(score.getValue());
    }
    ctx.respond("Scores: #([, ] %s: %#d)", (Object)args.toArray());
}
```

Note the cast to `Object` to avoid Java's [variadic terribleness](#constructing-messages) -- we're passing a single array of `Objects` to `ctx.respond()`, not the contents of the array.
