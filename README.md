An [LSP server](https://microsoft.github.io/language-server-protocol) is a program that can be used by an [IDE](https://en.wikipedia.org/wiki/Integrated_development_environment) to provide editing support for a programming language. Typeless is an LSP server for JavaScript that provides the same level of editor tooling for JavaScript as programmers get when using TypeScript. To do this, Typeless asks programmers to provide tests that it uses to analyse the code. Editor tooling provided by Typeless includes:

- Inline syntax and semantic errors
- Code completion, variable hover tooltips and function call help
- Code navigation such as go to definition and find references
- Assisted rename refactoring

Unlike other editor tooling available for JavaScript, Typeless does not require annotations and works smoothly across function boundaries.

### Great, how do I get it?
Typeless is currently in the design phase and can't be used. If you're interested in this tool, please star the GitHub page or upvote the newspost that brought you here. If you want to comment on the Typeless specification and have no please to do so, please leave your comment in a GitHub issue. You can also create a pull request to suggest changes to the Typeless design.

## Why use JavaScript and Typeless when I can use TypeScript?
Types in their simplest form, such as the type `number` or `string`, are easy to understand. However, as TypeScript applications get more complex so do the types required to describe them. The TypeScript handbook features a section called [Advanced types](https://www.typescriptlang.org/docs/handbook/advanced-types.html), which indeed can be used to write advanced types. Here's an example:

```typescript
type MyType<TType extends keyof FooTypesMap = 'never'> = {
    [k in keyof FooTypesMap]: {
        foo: TType extends 'never' ? k : TType,
        bar: FooTypesMap[TType extends 'never' ? k : TType],
        baz?: MyType,
        qux?: {
            [key: string]: MyType
        }
    }
}[keyof FooTypesMap]

interface FooTypesMap {
    never: never;
    one: string;
    two: boolean;
    three: {
        one: Date
    };
}
```

The type above has become a little program of its own, and understanding which values are part of this type requires mentally executing this type-level program. We want to offer programmers the opportunity to avoid doing such mental gymnastics and work with a type-free language.

Conceptually, we view type-checking as a way of formally proving that a particular class of errors does not occur in a program. Because compilers are limited in the extend to which they can provide these proofs automatically, the programmer is often required to provide type annotations to help the compiler. For programmers who are not interested in providing formal correctness proofs of their program, we want to offer a typeless programming experience.

## Why not use the JavaScript support in TypeScript's LSP server?
The existing TypeScript language server that's included in [the TypeScript repository](https://github.com/microsoft/TypeScript) can also be used to provide editor support for JavaScript programs. Here's an example:

```javascript
function foo() {
  var person = { name: Remy, age: 31 };
  person.
  // We get autocompletion for name and age after typing the dot

  person.name
  // Executing go to definition on "name" jumps to "name" in "{ name: Remy, age: 31 }";
}
```

However, the JavaScript language tooling provided by the TypeScript language server depends on type inference. It performs type inference within the body of a function, but not on the signatures of functions themselves, causing editor tooling to break down when doing function calls. Here's an example:

```javascript
function foo() {
  var person = { name: Remy, age: 31 };
  usePerson(person)
}

function usePerson(person) {
  person.
  // After typing the dot, no semantic code completion is provided. There is only textual code completion based on what other identifiers occur in this file, for example 'person' is in the list.
}
```

With type inference only working in parts of the program, type-based JavaScript editor tooling is not able to match TypeScript editor tooling.

## How does Typeless work?
Instead of requiring the programmer to write type annotations, Typeless requires them to write tests. Since programmers are already inclined to write tests, this may not be much of an burden. While the programmer is working, Typeless runs tests in the background to understand the program and provide editor tooling.

Typeless recognizes tests by looking for parameterless functions whose name ends in 'Test'. Here's an example:

```javascript
// The function 'fibonacciTest' is recognized as a test.
function fibonacciTest() {
  assert.equal(fibonnaci(3), 3);
  assert.equal(fibonacci(4), 5);
}

function fibonacci(n) {
  if (n < 2) return 1;
  return fibonacci(n-1) + fibonnacci(n-2);
}
```

# Examples of editing support by Typeless

## Inline errors

If a test throws an unhandled error, Typeless shows the error where it was thrown:
```javascript
function fibonacciTest() {
  assert.equal(fibonnaci(3), 3);
  assert.equal(fibonacci(4), 5);
}

function fibonacci(n) {
  return n.foo;
         ^^^^^^
  // The member 'foo' is not available on value '3'.
}
```

Diagnostics for syntax errors work as they already do in existing JavaScript tooling.

### What if the problem is not where the error was thrown?

A function named `X` can be *assigned* a test by creating a test function named `XTest`. Typeless assumes that as long as a function's test is passing, that function is `correct`. When a test fails while inside a call to a correct function, it concludes that the error was at the call site, not where the failure occurred.

The error is shown at the call site and provides examples of correct arguments that can be passed to the function, which are taken from the function's test.

```javascript
function highLevelTest() {
  fibonacci("hello");
            ^^^^^^^
  // The value "hello" passed to fibonacci is not valid. Examples of valid values are: 2, 3
}

function fibonacciTest() {
  assert(fibonnaci(2) == 3)
  assert(fibonacci(3) == 5)
}

function fibonacci(n) {
  if (n < 2) return 1;
  return fibonacci(n-1) + fibonnacci(n-2);
}
```

When errors occur in imported packages, Typeless assumes the fault is in the codebase running the test.
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

> Do we need better error messages when incorrectly calling library functions? Can we get example values?

> Can we treat TypeError in a special way to provide better feedback?

## Hover tooltips, code completion and function call help.
Typeless remembers the values of variables at different points during test execution, and uses this to show example values when hovering over a variable, when providing code completion and when providing function call help.

For example:
```javascript
function fibonacciTest() {
  assert(fibonnaci(2) == 3)
  assert(fibonacci(3) == 5)
}

function fibonacci(n) {
  // Hovering over the parameter n will show the values "2" and "3".

  return n.
  // After the dot, completion results for the numbers 3 and 4 are shown, such as toFixed, toExponential and toString.
}

function fibonacciUser() {
  fibonacci(
  // After the opening paranthesis, Typeless will suggest two options for calling fibonacci, one passing 2 and one passing 3.
}
```

Objects and array are only represented up to one level deep:
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
```
class Animal { constructor(age) { this.age = age; } }

function foo() {
  const x = new Animal(3);
  // Hovering over x will show 'Animal { age: 3 }'
}
```

### Documentation
The information provided by hover, code completion and function call help can be enhanced by adding documentation to the program. You can do this using JSDoc comments.

```javascript
/**
 * Given a number n, computes the n'th fibonacci number.
 * 
 * @param n which fibonacci number to compute
 * @return The n'th fibonacci number
 */
function fibonacci(n) {
  n
  // On hover over n, the documentation for n is shown together with example values 2 and 3.
}

function fibonacciTest() {
  assert(fibonnaci(2) == 3)
  assert(fibonacci(3) == 5)
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

Documentation remains attached to values when they traverse function boundaries:
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
}
```

## Go to definition
Typeless considers the following statements as definitions:
- a variable defined with `var`, `let` or `const`. 
- a named function defined with `function`
- an object that is assigned a member which it did not have yet through the dot syntax.
- a member in an object literal.
- a module exporting a member.

Note that if an object is assigned a new member through the bracket syntax, such as `remy['age'] = 32`, that is not considered a definition.

When executing 'go to definition' on a reference, Typeless will jump to the definition that is referenced.

A example of a definition resulting from a variable declaration:
```javascript
function variables() {
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

When a variable is reassigned the definition location is copied from the previous value to the new one.

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
Find references is like an inverse of 'go to definition'. When executed on a definition or a reference to a definiton, find references will show a list of the references to that definition.

Rename refactoring is implemented using the find references functionality, since it will renames a definition and all references to that definition.

Both find references and rename on definitions that are not local, which are definitions from object or module members, require Typeless to execute all tests in a program, which can take time in large programs. The only way to mitigate this slowness is to split a large program up into several packages.

For non-local definitions, Typeless will stream results for a 'find references' request. 

## Value origin tracking
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
  // The value 9 was expected but it was 6.
}
```

And another one:
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
Generators can be used to supercharge your tests. Generators are used to generate different test values to find edge cases in your code. The editor tooling will use your generators to generate different test values in the background while you're programming. When an interesting edge case is found, the editor tooling will suggest to explicitly add this value to your code so that it's always tested against when building your program.

The specification of generators is still a work in progress. Below is an example of what it might look like:

```javascript
function fibonaccisTest() {
  const n = generators.naturalNumbers.pop()
  assert.equal(fibonacciFast(n), fibonacciSlow(n));
}

function fibonacciSlow(n) {
  if (n < 2) return 1;
  return fibonacciSlow(n-1) + fibonacciSlow(n-2);
}

function fibonacciFast(n) {
  return fibonacciFastHelper(n)[1];
}

function fibonacciFastHelper(n) {
  if (n < 2) return [1, 1];
  const [two, one] = fibonacciFast(n-1);
  return [one, one + two];
}
```

## Strict mode
  
In strict mode, if any line of code is not hit by any test function, that's an error:
```javascript

fibonacciTest() {
  assert(fibonnaci(0) == 1)
  assert(fibonacci(1) == 1)
}

function fibonacci(x) {
  if (x < 2) return 1;
  return fibonacci(x-1) + fibonnacci(x-2);
  ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  // This line is not covered by tests
}
```

# Out of scope

The following editor tooling features provided by the LSP protocol are not implemented:
- textdocument/implementation does not apply
- textdocument/declaration does not apply
