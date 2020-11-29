# Work in progress

Whenever a test is running and a function is called, we first check to see whether this function has an assigned test and if so run that test first. This lets us know whether the called function is implemented correctly, and helps us assign blame with the original test fails.

Mutually recursive function work fine in this system.

A      B     CalledFirst Result
passes fails A           B failure shown where it occurs.
passes fails B           B failure shown where it occurs.

> Are mutually recursive functions problematic? One can have it's tests pass while the other has it's tests fail

## OPTION 1. Custom interpreter

We'll use an immutable data-structure to track the program state on each line, so we re-use the data-structure from one line to describe the program state in the next line.

One option for the data-structure it to maintain an event log that describes per line what variables are changed, and to layer a cache over that which stores computed program states based on the event log. By using a cache we'll only store program states that the programmer is actually requesting.

## OPTION 2. Use Node
We can use Node to run JavaScript with. When tests are changed, we'll delete changed modules and the modules that dependened on them from the require cache.

We can instrument each line so right before that line the values and metadata of each reference on that line is sent to the language server.

With a debugger we can stop on any line that contains definitions or references, and request the value of the variable including metadata such as source location and documentation.

## Other stuff
Each piece of cached data is tagged with the test that generated it. When a test run is invalidated, the data returned by that run is invalidated as well.

How do you cache data? How do you know which data is still relevant? Each piece of data can be tagged with a test, and each test can be tagged by which lines it hits.

Values can have metadata assigned to them, such as a source location and documentation. When a variable is defined, it is assigned an undefined value with source location metadata. Variable assignments always maintain the metadata of that variable's value.

Source location are assigned to literal values such as 3, "hello", and including functions. Invoking 'goto definition' on a variable will allow jumping to any of the source locations of the values that variable.


- Try to get goto definition working by wrapping values in a `withSourceLocation` wrapper when they're assigned to a variable. Why do we need this on assignment, instead of when the value literal is created? 


## Performance

The compiler remembers which lines are covered by which test, and if any of those lines change, the test is rerun. The IDE overlays per test how long it takes to run that test, so that the programmer knows what tests are taking too long. Possibly the compiler can make memory snapshots to allow re-running a test from where the code was changed.



```
function returnObject() {
  return { name: "Johan" }
  // the value of name has source location metadata
}

function returnObject2() {
  return { name: "Jaap" }
  // the value of name has source location metadata
}

function useObject(boolean) {
  return (boolean ? returnObject() : returnObject2()).name;
  // Goto definition on name jumps to where name was first assigned... ? Or where documentation for it was assigned?
}
```

Translated to:
```
function returnObject() {
  return { name: Object.assign("Johan", { sourceLocation: { .. } }) }
}

function returnObject2() {
  return { name: Object.assign("Jaap", { sourceLocation: { .. } }) }
}

function useObject(boolean) {
  return (boolean ? returnObject() : returnObject2()).name;
}
```

```
function multipleAssignments(boolean) {
  var x = 3;
  x = 4
  
}
```