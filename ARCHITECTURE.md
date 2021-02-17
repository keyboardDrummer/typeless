## What is it?
Typeless is an [LSP server](https://microsoft.github.io/language-server-protocol/) that provides editor tooling for JavaScript.

## Architecture
Typeless uses a custom JavaScript interpreter to parse and run your JavaScript code. The LSP server invokes this interpreter once to determine which tests are defined, and then once on each test, to compute diagnostics and the mapping between definitions and references. The mapping is used to provide support for goto definition, find references, rename and goto symbol.

For all LSP server requests such as hover, code completion and call signature support, Typeless requires knowledge of runtime values, and will invoke one or more tests to compute these values on the fly.

## Definitions and references
One thing the custom interpreter enables is resolving definitions and references. Definition sites are attached to values as metadata and travel alongside those values to where they're referred to. An important question is what constitutes a definition. For example, in the following program, where is the property 'name' defined?

```
const obj = {};
obj["name"] = "Jeroen";
obj.name = "Remy";
obj.name = "Elise";
obj.name 
// where does goto definition on the last expression jump to?
```

If there is a dot assignment, Typeless will attach the location of that assignment to the assigned value, but only if there wasn't already an assigned location to an existing value, otherwise it will copy the attached location from the existing to the new value. Goto definition on the last `obj.name` line jumps to `name` in `obj.name = "Remy"`.

## Trust levels
When an exception occurs in user code during test execution, Typeless may choose to show an error not where the exception occurred but on any of the calls in the exception callstack. The decision on where to place blame is made using trust levels. Typeless has three trust levels:

- Trusted: a function can have an associated test function. A function is trusted its associated test passes. Native functions such as `Array.prototype.reduce` are also trusted.
- IsTest: test functions and any functions or lambda's defined inside the body of those test functions
- Untrusted: any code that is not a test or trusted.

Given a call and an exception that occurred during that call, blame is place on the call if the trust level of the exception site is higher than that of the call site.

You might expect that any time an untrusted function calls a trusted function, any exception must be blamed on the calling function. However, this is not the case for higher order trusted functions, since they may call untrusted functions.

## Error correction
Because it's important to provide code completion while the programmer is typing, which is usually when the program does not parse, Typeless performs parse error correction on programs, after which it runs its interpreter on the repaired program. A simple example is the following:

```
const remy = { name: "Remy" }
remy.
// Code completion for name is shown.
```

## Execution budget
Typeless allocates an execution budget for each test, which is drained for each interpreted operation during the test. If a test runs out of budget, an exception is thrown at that point.

Users can annotate tests to customise the execution budget in case they have a particularly large test, but this may impact the responsiveness of Typeless. In general, tests written for Typeless should use small datasets and not tests any performance.

