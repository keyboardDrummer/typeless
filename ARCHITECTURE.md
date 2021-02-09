
### What's the simplest solution?
Program state for the interpreter mirrors what the programmer is using.
For every test we know which lines of code it covers. 
If any of those lines change, the test is marked dirty.
Dirty tests are queued for being re-run. Tests when run provide diagnostics. Those diagnostics are removed when the test is marked dirty.

If the programmer requests information at a particular code point, we run a test that covers this line and provide information using that.

### What can we do to speed things up?

We can store code navigation and variable value information after every test run. However, this costs memory. We can implement a sort of garbage collection, where we prioritise storing information about elements in files that the user has open, and files that can be reached from there, but information of files that are too 'deep' is thrown away.

### Do we want immutable program state for the interpreter?

Advantage: when code is changed, interpretation can be run right from the changed point, which is exactly where we need it.

Disadvantage: using immutable data-structure changes the performance characteristics of the program. What's the worst case?
Storing the program state at every debug point can cost a lot of memory. We can also store it every X debug points.

### How do we connect the information available at debug points to LSP requests?
LSP requests map to elements in the AST.
When we interpret an AST element, we assign the resulting value to that AST element.