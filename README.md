Datastar is a library/framework for making websites. It claims to rival SPA frameworks such as React in terms of expressivity and performance. Well, it actually claims to blow those frameworks out of the water.

The core functionality seems to be: rely on your backend to do all the real work and just send down HTML with Datastar attributes, and datastar will handle keeping the DOM updated. And this will be fast (assuming your backend is fast). Datastar provides "attribute plugins" that read "data-\*" HTML element attributes and handle them in useful ways such as maintaining state (data-signals) and handling events (data-on-click). Datastar also provides "action plugins" that can be invoked from "data-*" attributes, for instance to send an http request.

Datastar claims this architecture will enable us to write less code and have faster applications.

I mainly build business CRUD apps, so I'm less interested in super-fast renders and more interested in UIs with lots of elements for controlling lots of different things. Can I make those types of UIs?

The remaining sections of this document will describe the experiments I'm doing to test out Datastar, answer some questions I had, and share my general thoughts on this thing. I used Clojure for my backend, but Datastar has lots of backend SDKs, which mainly just handle sending the server-sent events Datastar's backend action plugins expect.

tl;dr It works. I'm concerned that signals would proliferate and turn into a mess in a page with a lot of interactive elements.

# Examples

Each Clojure namespace under ./examples/ is an independent example. I tried to keep them all self-contained (except for library code) to make the examples easier to quickly read through.

To start a repl for playing with the examples:

```bash
cd ./examples
clojure -M:dev:cider:repl
```

You can then connect to that from CIDER.

## Hello World

This is just a simple hello world, based off the Clojure hello world in Datastar's repo: https://github.com/starfederation/datastar/tree/main/examples/clojure/hello-world.

We could just clone that but then I wouldn't learn much.

My code is at ./examples/src/main/hello/world1.clj

A note about the hello world in datastar's repo:
- it would probably be better to use local roots for the sdk and adapter only under a particular alias, because I can't just copy and paste the current deps.edn into a new project without changing those dependencies.

The datastar hello world uses the datastar ring adapter to send SSEs to the client to spell out a message over an interval, with a configurable duration between characters.

We're only using 4 things from the datastar sdk:
- `->sse-request`
- `on-open`
- `with-open-sse`
- `merge-fragment!`

`on-open` is just a keyword in a var. This gives us a nice docstring to look at. The value we must pass for that key is a function which takes an SSE connection.

I have typed "see" instead of "sse" 1 million times so far. That might be the most damning quality of datastar.

`->sse-request` handles making a ring response that allows us to use the same connnection to send server-sent events. This basically turns the 1:1 correspondence between requests and responses to a 1:many, where many includes 0.

I believe the :write-profile option of `->sse-response` is where I'd look to add compression to the response stream.

`with-open-sse` is just `with-open` for a SSE event stream. The adapter code calls this `sse-gen`, because it's an object that _gen_erates SSE events.

Finally, `merge-fragment!` is how we send chunks of HTML to he browser for datastar to merge into the DOM. This function basically just formats the message in the way datastar expects (ultimately we're just dealing with text) and sends it down.

If you look in the network tab of your dev tools you'll see "datastar-merge-fragment" events coming down and the data associated with each is HTML sent through `merge-fragment!`.

### My Changes

The hello world is easy enough to understand so I'll make it a bit more complicated: the user chooses the message that the server will stream back down.

I did this in ./examples/src/main/hello/world2.clj

I needed to modify the html of the initial page, so I converted that to hiccup and made my changes in there.

The first thing I got wrong here was thinking I could just add the label and input for the message and it would get included in the request to the server:

``` clojure
[:div.space-x-2
 [:label {:for "message"} "The message to send"]
 [:input {:data-bind "message"
          :id "message"
          :type "text"
          :class "..."}]]
```

One must declare the signal. That's the second thing I got wrong:

```clojure
[:div {:data-signals-delay "400"
       :data-signals-message "Hello World"
       :class "..."}
 ...]
```

That's just not the right way to use the data-signals plugin. This syntax worked better:

```clojure
[:div {:data-signals "{delay: 400, message: 'hello world'}"
       :class "..."}
 ...]
```

I could also put JSON in there, generated from a clojure data structure.

The above got the html onto the page the way I expected. I don't love that those two forms behave differently, but I suppose there's a reason for that.

We're still not there yet though, because I used the id "message" for the message input, and there's already an element with that id. Datastar doesn't want us to do that but it also doesn't crash. ID collisions are always something to watch out for in HTML and anything else with a concept of an ID, so in my SPA days I typically just don't put IDs on elements or randomly generate them. But these inputs are referenced by the <label> "for" attribute.

Fix the ID collision and the demo works as expected.

I was curious if changing the request method for the request that sets up the SSEs for loading the message characters would change anything. I made it a POST. It sent a POST request and everything worked the same.

This demo was my first hint that signals might proliferate a bit more than the userbase lets on; the advice is to use no more than a handful of signals per page, but here we're using a signal per field of our form. Any complicated page using the same approach will therefore use dozens or hundreds of signals.

## Internal Element State

Datastar allows us to use the semantics of replacing the entire DOM but retain the internal state of specific DOM elements.

Let's say we have a page with 4 spinning elements and a "reorder" button. When we reorder, we get the entire page back from the server w/ the elements in a new order. Is animation position lost?

This matter is mainly handled by idiomorph, which is the library that datastar uses to integrate DOM updates from the server into the live DOM.

This example is in namespace `hello.element-state`. I'm using Garden for the CSS because don't know how to use Tailwind.

One of my biggest hangups working on this was that the Chrome devtools are not showing me some of the SSEs in the EventStream tab. I don't know what's up with that. I /think/ the problem only happens when there's only a single event in the event stream.

Something to be aware of with `merge-fragments!`: this won't reorder elements in the DOM based on the order of calls, and that should be obvious to anyone with a brain.

Once I got things set up correctly I found that while Datastar is merging the fragments correctly, apparently DOM element animations are reset when removed and reinserted, which is necessary(?) to reorder elements. So in this example, when an element moves from the beginning of the list to the end its animation is reset. I bet you could work around that by using CSS positioning instead. Anyway, my apps don't often have grids of rotating images, so this works well enough for me.

Open question: What role do element IDs play in performance here? If I'm reloading the whole `#pictures` element, do the IDs on the `img` elements affect anything?


## Optimistic Update

The creator of Datastar seems to have ethical concerns about optimistic update. I do not share those concerns. When my user makes a change, I want to show them the result of that change immediately, even if it means briefly showing them a state that doesn't match the server's state.

My demo for this is at `hello.optimistic-update`.

I set up a single checkbox that toggles a backend atom between true and false. The first issue I hit is that datastar's data-attr plugin complains if the attribute has no value, but HTML wants unchecked checkboxes to exclude the "checked" attribute entirely.

So this code is bad:

```clojure
[:input {:type "checkbox"
         :data-attr-checked @rsvp*
         :data-on-change "@post('/rsvp')"
         :id "rsvp-input"}]
```

So I tried this:
```clojure
[:input {:type "checkbox"
         :data-attr (write-json {:checked @rsvp*})
         :data-on-change "@post('/rsvp')"
         :id "rsvp-input"}]
```

and that does correctly update the "checked" attribute of the checkbox. BUT! the DOM doesn't automatically check the box when "checked" attribute is added. I'm not sure I understand what's going on here because I do see a brief period where the checkbox is checked before the merge-fragment event comes down from the server, so I'm not sure why it's getting unchecked.

Anyway, it seems like signals are the better way to deal with this.

```clojure
[:input {:type "checkbox"
         :data-signals (write-json {:checked @rsvp*})
         :data-bind "$checked"
         :data-on-change "@post('/rsvp')"
         :id "rsvp-input"}]
```

But it's probably not a good idea to define the signal on the same element that uses data-bind when using hiccup, because we don't have a guarantee that "data-signals" will be before "data-bind" in the generated html.

Regarding the purpose of this experiment, optimistic update, we basically get that by default in simple cases like this. The checkbox state changes as soon as we toggle it, and the server's response doesn't modify it.

If the `rsvp*` atom toggle fails (like if you comment out that line of code) then the input will return to its previous state when the response comes back.

This experiment further suggests that signals will proliferate in a real app.


## Bigger Test App

Make a single app that tests:
- [x] dnd
- [ ] rich text editing
- [x] shoelace components like popovers
- [ ] undo/redo
- [ ] notifications
- [ ] error handling

This is located at `hello.cannibal-kitchen`. It's not done.

My thoughts while implementing this:

Interpolating stuff into JS expression does kind of suck. For example:

```clojure
:data-on-dragstart (str "evt.dataTransfer.setData('text/plain', " id "); evt.dataTransfer.dropEffect = 'copy';")
```

See the bug? The id isn't quoted in the resultant js.


Here's another interpolation challenge:

``` clojure
{:data-class (write-json {:droppable "$dragging.type === 'employee'"})}
```

As is obvious upon a moment's inspection, we can't use JSON here because we need to embed CODE.

So we need:

```clojure
{:data-class "{droppable: $dragging.type === 'employee'}"}
```

It's sort of painful to write code in a language as nice as Clojure but then have to write code in strings for all the Datastar stuff.

Signals are global. That creates challenges when, for example, I want some part of the UI to have some state for toggling a class. Now I have to make sure no other part of the UI chooses the same name. Namespaces help a little bit with that, but it would be nicer to be able to know that a signal is confined to a limited scope. It seems the current Datastar userbase isn't concerned about this at all, and there's no coherent story yet for how you'd manage a big complicated UI.


# Q/A
## Security?
### Script Injection

It occurs to me that the above examples are vulnerable to script injection because I'm concatenating strings with user-provided inputs into code.

A quick fix is to do something like:

```clojure
[:div {:data-signals (d*-expr "{foo: #bar}" {:#bar "some user-provided value"})}]
```

where `d*-expr` properly escapes the variables, perhaps just by writing the values to JSON.

An even better option would be a clojure-to-js templating DSL that allows using Clojure's syntax with javascript's semantics. Clojurescript and Squint are too much for this use case. I'm not sure exactly what this would look like. It would be a bit like HoneySQL in that we'd be safely mixing code and data, but I'd prefer to use a listy syntax. That gets a bit complicated though:

```clojure
;; We need something like quote/unquote to allow reaching out of the template.
`(= $signal ~(get-user-provided-input)) ;; => "$signal = \"the user's input\""
```



## Can I Use Material UI, or other existing component libraries?

Material UI makes it easy to build nice-looking UIs with React. Can I use it by compiling to Web Components?

No. But there are good libraries of web components. My favorite that I found is [shoelace](https://shoelace.style/).


Also, you can use [Lit](https://lit.dev/docs/) to build your own web components. Or just write them by hand. Lit really just makes it a bit more convenient. The web component authoring process really isn't that different from Svelte or React.

You can also define web components with [Svelte](https://svelte.dev/docs/svelte/custom-elements).

## Can I do rich text editing?

Yeah. It looks like the best way to do that is to use Prosemirror, perhaps wrapped in a web component. See:
- https://prosemirror.net/
- https://mishareyzlin.com/notes/prose-mirror-web-component-part-1



## Can I Do Drag and Drop?

Yes. This is in the bigger demo I did, in the namespace `hello.cannibal-kitchen`.

I found that all the app features business users expect like drag and drop, rich text, and customized inputs are doable with web components or just modern HTML shit, and this really has little to do with Datastar.


## How to handle undo/redo?

The undo/redo state per user should be backend state. Whenever we submit a change, we update that store. We allow clients to send undo and redo requests up to the server. We can basically make undo/redo not a client concern at all.

If we do targeted DOM updates, we'll send down an #undo-redo-area element on all mutations. If we don't, the whole dom will just include the undo-redo buttons when those options are available.


## What about notifications and notification history?

Like "save failed" and "save succeeded". These could be included in the response to mutations, but then we'd need to retain notification history on the backend which is sort of annoying since these really only matter for one session.

It seems like we might want a separate DOM tree for the notifications.

Or we _could_ just retain notifications in the backend storage and just expire them after some amount of time. Probably that.

## Can I use squint to generate the javascript?

[Squint](https://github.com/squint-cljs/squint) is like ClojureScript but uses JavaScript's data structures. It might be nice to use this to produce the Datastar expressions we put in attributes like `data-on-click`.

Squint has a runtime - I would want something that only does the syntax translation. There appear to be some options, none very active.

Perhaps a Datastar+Clojure -specific tool could provide a nicer syntax for common stuff.

## Charts

What's the best way to do charts? In the SPA world I'd use one of the many existing charting libraries. Maybe using d3 directly is the best choice. This question has very little to do with Datastar.

See:
- https://geneshki.com/how-i-made-chart-js-sane-by-wrapping-it-in-a-web-component/
- https://www.chartjs.org/docs/latest/charts/area.html

## What's the deal with server-sent events?

Sort of like websockets, but instead of an initial http request to set up and then 2-way comms, there's an initial http request to set up and then only server->client comms.

You should use http2 (or higher) if your app uses SSEs.

That's all you need to know about SSEs to use Datastar.

## How do I use http2?

I should actually use http3 if I can. It's like http2 but one louder.

Looks like the Clojure app won't deal w/ http2+ at all, and instead I'll just deal with that on the nginx side.

The [nginx listen directive](https://nginx.org/en/docs/http/ngx_http_core_module.html#listen) supports "http2" and "quic", so I can apparently use http2 or 3 as long as the nginx build was compiled w/ support for those protocols.

Also see [this serverfault post](https://serverfault.com/questions/1158832/nginx-1-26-can-http3-coexist-with-http2) about using both for the same site.


## When and how should I use javascript in my datastar apps?

The "how" part is easy - just the load script w/ a script tag. "When" is trickier. We need js for defining web components. Any js that deals with client-side state is probably a mistake. Maybe it is sometimes useful to have additional library code?

## Hyperlith?

[Hyperlith](https://github.com/andersmurphy/hyperlith) is a framework/bunch of utilities for making Datastar apps made by an early Datastar adopter, Anders Murphy. It does a lot of things that you should consider doing in your app. It probably makes sense to copy the code into your app and adjust as needed, rather than relying on Hyperlith directly, simply because it doesn't appear that Anders wants Hyperlith to need to be stable right now.

Interesting stuff in Hyperlith:
- Brotli compression
- caching/memoization
- url and form encoding/decoding
- datastar SSE event formatting
- http response header utilities

## Error Handling How?

If a request fails at the server or there's a network timeout, we should inform the user.

It appears Datastar's creator doesn't agree with that? But it appears he eventually agreed to support error handlers for Datastar's http plugins. So that's how you'd do http request error handling, which is probably the only kind of error a Datastar app really needs to deal with since there should be very little client-side state.


## How do I use Brotli compression?

I can use it in nginx or directly from the jvm process. IDK what the tradeoffs are.

- Nginx setup: https://www.brotli.pro/enable-brotli/servers/nginx/
- Brotli4j: https://github.com/hyperxpro/Brotli4j
  - Note hyperlith has a brotli wrapper at hyperlith.impl.brotli

## When don't datastar?

Don't use datastar if:

- clients must be able to use the app offline
- (and this is where the Datastar people will probably disagree) your app UI has a large variety and number of interactive elements. Because that will lead to a proliferation of signals which would quickly become a mess.

## What is the Datastar Way?

- Use few signals
- As little client-side state as possible. If you can go to the server to read/write some state, do so.
- Use existing web standards to do things declaratively instead of using javascript.

# Final Thoughts

My concerns are no longer about doing rich text editing, undo/redo, or notifications. Rich text can be done with Prosemirror. Undo and redo can be managed server-side and will be better than client-side managed undo/redo. Notifications can also be managed server-side and will be better than client-side managed notifications.

I'm mostly concerned that implementing a truly complicated web page will require a huge number of signals. The advice I'm getting in the Datastar discord is to use only a handful of signals per page, but the examples all seem to contradict this advice, frequently using one signal per field. Imagine something like Tableau - a single page could easily have several hundred interactive things. I'm not convinced Datastar's global signal approach will work well in that situation.


I haven't paid enough attention to built-in HTML/DOM/JS stuff in recent years. There are lots of modern HTML and CSS features that replace stuff found in SPA frameworks. Learn about them and use them:

- CSS variables
- Speculation Rules (preload linked pages): https://www.debugbear.com/blog/speculation-rules#an-introduction-to-speculation-rules
- View Transitions (animate from one page/document to the next): https://www.debugbear.com/blog/view-transitions-spa-without-framework#what-are-view-transitions
