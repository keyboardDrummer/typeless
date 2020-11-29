Typeless is an LSP server for JavaScript that provides the same level of editor tooling for JavaScript as programmers get when using TypeScript. To do this, Typeless asks programmers to provide tests that it uses to analyse the code.

## What about JavaScript support in the existing TypeScript LSP server?

The JavaScript language tooling provided by the TypeScript server depends on type inference. It performs type inference within the scope of a function, which is great. The following example showcases this:

```javascript
function foo() {
  var person = { name: Remy, age: 31 };
  person.
  // We get autocompletion for name and age after typing the dot

  person.name
  // Executing go to definition on "name" jumps to "name" in "{ name: Remy, age: 31 }";
}
```

However, type inference is not performed at the function level, which you can see in this example:

```javascript
function foo() {
  var person = { name: Remy, age: 31 };
  usePerson(person)
}

function usePerson(x) {
  x.
  // After typing the dot no semantic code completion is provided. There is only textual code completion based on what other identifiers occur in this file, for example 'person' is in the list.
}
```

With type inference only working in parts of the program, type-based JavaScript editor tooling is not able to match TypeScript editor tooling.

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

## Diagnostics

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

Diagnostics for syntax error work as they already do in existing JavaScript tooling.

### What if the bug is not where the error was thrown?

A function can be assigned a test by creating a test with the same name. We assume that as long as a function's test is passing, that function is valid. When another test then fails while in this function, we conclude that invalid parameters were passed. 

The error is shown at the call site and provides examples of correct arguments that can be passed to the function which are taken from the function's test.

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

When errors occurs in imported packages, Typeless assumes the fault is in the codebase running the test.
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

> Do we have issues with global state?


## Hover, code completion and signature help.
Typeless remembers the values of variables at different points during test execution, and uses this to show examples values when hovering over a variable, when providing code completion and when providing call signature help.

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
  // After the opening paranthesis, Typeless will suggest two signatures for fibonacci, one passing 2 and one passing 3.
}
```

Complex values will only be represented up to one level deep:
```javascript
function fooTest() {
  foo({ name: 'Remy', favoriteColors: ['Brown', 'Green']})
}

function foo(value) {
  value
  // Hovering over value will show { name: "Remy", favoriteColors: [..] }
}
```

> How do we represent values in general? We only have markdown to work with. I guess programmers can select values from objects by writing more code..

> Do we show the ```__proto__``` field of values?

### Documentation
The information provided by hover, code completion and signature help can be enhanced by adding documentation to the program. You can do this using JSDoc comments.

```javascript
test fibonacci() {
  assert(fibonnaci(2) == 3)
  assert(fibonacci(3) == 5)
}

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

function highLevel() {
  fibonacci
  // On hover over fiboancci, the documentation for fibonacci is shown.
  fibonacci(
  // Signature help is provided, showing the documentation for the parameter n and example values 2 and 3.
  const result = fibonacci(5);
  // On hover over "result", the documentation for the return value of fibonacci is shown.
}
```

Documentation can be attached to object members by returning the object from a function, like so:
```javascript
/**
 * @param name the name of the person
 * @param age the age of the person
 * @returns person
 * @returns person.name the name of the person
 * @returns person.age the age of the person
 */
function Person(name, age) {
  this.name = name;
  this.age = age;
}
```

> Can we get an example without as much repetition as the above one?

## Go to definition
Typeless considers the following statements as definitions:
- a variable defined with `var`, `let` or `const`. 
- a named function defined with `function`
- an object that is assigned a member which it did not have yet through the dot syntax.
- a member in an object literal.
- a module exporting a member.

Note that if an object is assigned a new member through the bracket, that is not considered a definition.

When executing 'go to definition' on a reference, Typeless will jump to the definition that is referenced.

A example of a definition resulting from a variable declaration:
```javascript
function multipleAssignments() {
  var x = 1;
  x = 2;
  x = 3;
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
  // Goto definition on name returns both "name" in "name: 'Johan'" and in "name: 'Jaap'"
}
```

> Do we need special consideration for things like Object.assign?

## Find references and rename
Find references is like an inverse of 'go to definition'. When executed on a definition or a reference to a definiton, find references will show a list of the references to that definition.

Rename relates strongly to find references, since it will rename a definition and all references to that definition.

Both find references and rename on definitions that are not local, which are definitions from object or module members, require Typeless to execute all tests in a program, which can take time in large programs. The only way to mitigate this slowness is to split a large program up into several packages.

For non-local definitions, Typeless will stream results for a 'find references' request. 

## Value origin tracking
Whenever a new value is created, Typeless will remember the location where that value was created together with the value. This 'value origin' is used for the following features.

### Go to type definition
In Typeless there are no types, just values, so the LSP command 'Go to type definition' changes its meaning to 'go to value definition'. When this command is executing on a variable, it will jump to where the value that is assigned to this variable was created.

```javascript
function usePersonTest() {
  usePerson();
}

function usePerson() {
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

> Improve the below example.

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
  
If any line of code is not hit by any test function that's an error:
```javascript

test myFirstTest() {
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

textdocument/implementation does not apply
textdocument/declaration does not apply