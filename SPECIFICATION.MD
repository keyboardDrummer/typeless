# How do I use Typeless?
Typeless runs unit tests to learn things about the source code under test, so without tests Typeless is useless. To get Typeless support while writing code, developers should use test-driven development. Once written, any code with good test coverage will get support from Typeless, whether it was written in a test-driven approach or not.

Typeless recognizes tests by looking for parameterless functions whose name ends in 'Test'. Here's an example:

```javascript
// The function 'fibonacciTest' is recognized as a test.
function fibonacciTest() {
  assert.equal(fibonacci(3), 3);
  assert.equal(fibonacci(4), 5);
}

function fibonacci(n) {
  if (n < 2) return 1;
  return fibonacci(n - 1) + fibonacci(n - 2);
}
```

## Inline errors

If a test throws an unhandled error, Typeless shows the error where it was thrown:
```javascript
function fibonacciTest() {
  assert.equal(fibonacci(3), 3);
  assert.equal(fibonacci(4), 5);
}

function fibonacci(n) {
  return n.foo;
         ^^^^^^
  // The member 'foo' is not available on value '3'.
}
```

Inline errors for syntax errors work as they already do in existing JavaScript tooling.

### What if the problem is not where the error was thrown?

A function named `X` can be assigned a test by creating a parameterless function named `XTest`. Typeless assumes that as long as a function's test is passing, that function is *correct*. When a test fails while inside a call to a correct function, it concludes that the error was at the call site, not where the failure occurred. The error is then shown at the call site together with examples of arguments that can be passed to the function, which are taken from the function's test.

```javascript
function highLevelTest() {
  fibonacci("hello");
            ^^^^^^^
  // The value "hello" passed to fibonacci is not valid. Examples of valid values are: 2, 3
}

function fibonacciTest() {
  assert(fibonacci(2) == 1)
  assert(fibonacci(3) == 2)
}

function fibonacci(n) {
  if (n < 2) return 1;
  return fibonacci(n - 1) + fibonacci(n - 2);
}
```

When an error occurs in an imported package, Typeless assumes the fault is in the call to the imported package.

```javascript
var fs = require('fs');

function testFoo() {
  foo();
}

function foo() {
  fs.writeFileSync(3);
                   ^
  // The value '3' passed to writeFileSync is not valid.
}
```

## Hover tooltips, code completion and function call help.
Typeless remembers the values of variables at different points during test execution, and uses this to show example values when hovering over a variable, when providing code completion and when providing function call help.

For example:
```javascript
function getNameTest() {
  const remy = { name: "Remy", age: 32 };
  assert.strictEquals(getName(remy), "Remy");
}

function getName(person) {
  // Hovering over the parameter person will show the value { name: "Remy", age: 32 }.

  return person.
  // After the dot, completion results for the 'name' and 'age' are shown
}

function getNameUser() {
  getName(
  // After the opening parenthesis, Typeless will suggest calling fibonacci with { name: "Remy", age: 32 }.
}
```

Objects and arrays are only represented up to one level deep:
```javascript
function fooTest() {
  foo({ name: 'Remy', favoriteColors: ['Brown', 'Green']})
}

function foo(value) {
  value
  // Hovering over value will show { name: "Remy", favoriteColors: [..] }
}
```
To inspect the value of `favoriteColors` in the above example, the programmer would need to write the expression `value.favoriteColors` somewhere.

If an object has a __proto__ field containing a named constructor, then the constructor name is shown before the objects opening brace:
```javascript
class Animal { constructor(age) { this.age = age; } }

function foo() {
  const x = new Animal(3);
  // Hovering over x will show 'Animal { age: 3 }'
}
```

## Documentation
The information provided by hover, code completion and function call help can be enhanced by adding documentation to the program. You can do this using JSDoc comments.

```javascript
/**
 * Given a number n, computes the n'th fibonacci number.
 * 
 * @param n the fibonacci number to compute
 * @return The n'th fibonacci number
 */
function fibonacci(n) {
  n
  // On hover, the documentation 'the fibonacci number to compute' is shown together with example values 2 and 3.
  
  n = 4
  // On hover over n, the documentation 'the fibonacci number to compute' is shown together with example value 4.
}

function fibonacciTest() {
  assert(fibonacci(4) == 3)
  assert(fibonacci(5) == 5)
}

function highLevelTest() {
  fibonacci
  // On hover over fibonacci, the documentation for fibonacci is shown.
  fibonacci(
  // Function call help is provided, showing the documentation for the parameter n and example values 2 and 3.
  const result = fibonacci(5);
  // On hover over "result", the documentation for the return value of fibonacci is shown.
}
```

Documentation remains attached to values when they traverse function boundaries.

```javascript
/**
 * @param name the name of the person
 * @param age the age of the person
 */
function Person(name, age) {
  this.name = name;
  this.age = age;
}

function PersonTest() {
  const remy = new Person("Remy", 32);
  remy.name
  // The hover tooltip over name shows the documentation 'the name of the person' as well as the value 'Remy'
  
  remy.name = "Elise";
  // The hover tooltip over name shows the documentation 'the name of the person' as well as the value 'Elise'

  remy["name"] = "Jacques";
  remy.name
  // The hover tooltip over name shows the value 'Jacques', but no documentation.
}
```

We can summarize some of what the above examples show by stating that documentation is carried together with values. If a variable is re-assigned, then documentation on the existing value is copied to the new value.

<!-- When is the documentation first assigned? When the function is called? How to we prevent from changing documentation on the caller side value that was passed to the function? Should be fine -->

## Go to definition
Typeless considers the following statements as definitions:
- a variable defined with `var`, `let` or `const`.
- a named function defined with `function`
- an object that is assigned a member which it did not have yet through the dot syntax: `var obj = {}; obj.fresh = 0`
- a member in an object literal: `var obj = { def: "bar" }`
- a module exporting a member.

Note that if an object is assigned a new member through the bracket syntax, such as `remy['age'] = 32`, that is not considered a definition.

When executing 'go to definition' on a reference, Typeless will jump to the definition that is referenced.

An example of a definition resulting from a variable declaration:
```javascript
function variablesTest() {
  const x = 2;
  return x + 3;
  // Goto definition on x will jump to "x" in "const x";
}
```

Definition locations are attached to the value of the definition, so they travel together with that value.

```javascript
function travel() {  
  var obj1 = { name: 'Remy' }
  var obj2 = Object.assign({}, obj1);
  obj2.name
  // Goto definition on name will jump to `name` in `{ name: 'Remy' }`
}
```

When a variable is reassigned, the definition location is copied from the current value to the new one.

```javascript
function reAssignment() {
  var x; // Current value is 'undefined', to which the definition location is attached
  x = 2; // Definition location is copied from 'undefined' to '2'
  // Goto definition on x will jump to "x" in "var x";
}
```

An example of a definition resulting from member assignment:
```javascript
function memberAssignments() {
  var obj = {};
  obj["name"] = 'Jeroen';
  obj.name = 'Remy';
  obj.name = 'Elise';
  // Goto definition on name will jump to "name" in the assignment "obj.name = 'Remy'";

  delete obj.name;
  obj.name = 'Jacques';
  // Goto definition on name will jump to "name" in the assignment "obj.name = 'Jacques'";
}
```

Unlike in typed languages, an object member can have multiple definition locations.
```javascript
function useObjectTest() {
  useObject(true);
  useObject(false);
}

function createJohan() {
  return { name: 'Johan' }
}

function createJaap() {
  return { name: 'Jaap' }
}

function useObject(boolean) {
  const person = boolean ? createJohan() : createJaap();
  return person.name;
  // Goto definition on name returns links to both "name" in "name: 'Johan'" and in "name: 'Jaap'"
}
```

## Find references and rename refactoring
Find references is like an inverse of 'go to definition'. When executed on a definition or a reference to a definition, find references will show a list of the references to that definition.

Rename refactoring is implemented using the find references functionality, since it will renames a definition and all references to that definition.

Both find references and rename on definitions that are not local, which are definitions from object or module members, require Typeless to execute all tests in a program, which can take time in large programs. The only way to mitigate this slowness is to split a large program up into several packages.

For non-local definitions, Typeless will stream results for a 'find references' request.

## typeless.interpreter.Value origin tracking
Whenever a new value is created, Typeless will remember the location where that value was created together with the value. This 'value origin' is used for the following features.

### Go to value created
In Typeless there are no types, just values, so the IDE command 'Go to type definition' changes its meaning to 'go to value creation'. When this command is executed on a variable, it will jump to where the value that is assigned to this variable was created.

```javascript
function PersonTest() {
  const person = new Person("Remy");
  return person.name;
  // Go to type definition on 'name' will jump to the literal "Remy"
}

function Person(name) {
  this.name = name;
}
```

### Validating values
When validating values using `assert.equals`, or an equivalent function, Typeless will show an error where the incorrect value was created instead of in the test. Here's an example:

```javascript
function squareTest() {
  assert.equal(square(2), 4);
  assert.equal(square(3), 9);
}

function square(x) {
  return x + x;
         ^^^^^
  // The value 9 was expected but it was 6, with a link to the assert.equal that caused this error.
}
```

Another one:
```javascript
function nameTest() {
  assert.equal(new Person("Remy").name, "Remy");
}

function Person(name) {
  this.name = "Elise";
              ^^^^^^^
  // The value "Remy" was expected but it was "Elise".
}
```

## Generators
Generators can be used to supercharge your tests. Generators are used to generate different input values to find edge cases in your code. Here's an example:

```javascript
function fibonacciSlow(n) {
  if (n < 2) return 1;
  return fibonacciSlow(n-1) + fibonacciSlow(n-2);
}

function fibonacciFast(n) {
  return fibonacciFastHelper(n)[1];
}

function fibonacciFastHelper(n) {
  if (n < 2) return [1, 1];
  // Implementation is missing.
  // const [two, one] = fibonacciFast(n-1);
  // return [one, one + two];
}

@seed(0, 2)
function fibonacciFastTest() {
  const n = generators.naturalNumbers.pop()
  assert.equal(fibonacciFast(n), fibonacciSlow(n));
}
``` 

Typeless runs the test `fibonacciFastTest` with different random seeds which in turn produce different values of `n`. Typeless records which random seeds execute which lines of code and which cause the test to fail. In the above code, Typeless discovers that the random seeds `0` covers specific lines, and that the seed `2` covers other lines and also fails the test. Typeless will suggest adding the decorator `@seed(0,2)` above the test `fibonacciFastTest` so that when Typeless is ran purely as a test engine, likely as part of a build step, it runs the test with those seed values.

## Strict mode

In strict mode, if any line of code is not hit by any test function, that's an error:
```javascript

fibonacciTest() {
  assert(fibonacci(0) == 1)
  assert(fibonacci(1) == 1)
}

function fibonacci(x) {
  if (x < 2) return 1;
  return fibonacci(x-1) + fibonacci(x-2);
  ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  // This line is not covered by tests
}
```