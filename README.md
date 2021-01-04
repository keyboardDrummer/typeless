# Typeless: all the benefits of TypeScript, without the types

Typeless provides the great editor tooling we're used to from TypeScript, but then for plain JavaScript. Typeless uses unit tests instead of type annotations to understand source code. Let's compare a Typeless program with its TypeScript equivalent. First the Typeless program:

```javascript
function pipeTest() {
  const plusOneTimesTwoMinusOne = pipe(x => x + 1, x => x * 2, x => x - 1);
  assert(1, plusOneTimesTwoMinusOne(0))
  assert(3, plusOneTimesTwoMinusOne(1))
}
const pipe = (...fns) => p => fns.reduce((acc, cur) => cur(acc), p);
// When typing 'fns.' code completion for arrays is shown.
// When typing 'reduce(', example arguments like 'x => x + 1' are shown. 
// Hovering over 'cur', 'acc' or 'p' shows us the values these variables can get when running the test.

function isNameOfEvenLengthTest() {
  assert(true, isNameOfEvenLength({ name: "Remy" }))
  assert(false, isNameOfEvenLength({ name: "Elise" }))
}
const isNameOfEvenLength = pipe(person => person.name, str => str.length, x => x % 2 == 0)
// When typing 'pipe(', example arguments to pipe such as 'x => x + 1' are shown.
// When typing 'person.', code completion suggests 'name'.
// When typing 'str.', code completion for string members is shown.

const isRemyEven = isNameOfEvenLength({ name: "Remy" })
// Hovering over isRemyEven shows us that it can have the values 'true' and 'false'.
```
Note how in the above code the tests are written before the code under test. Test-driven development is needed for Typeless to work well.

Now let's look at the TypeScript equivalent:

```typescript
type ArityOneFn = (arg: any) => any;
type PickLastInTuple<T extends any[]> = T extends [...rest: infer U, argn: infer L ] ? L : never;

const pipe = <T extends ArityOneFn[]>(...fns: T) => 
  (p: Parameters<T[0]>[0]): ReturnType<PickLastInTuple<T>> => 
  fns.reduce((acc: any, cur: ArityOneFn) => cur(acc), p);

interface IPerson {
  name: string
}

const isNameOfEvenLength = pipe((person: IPerson) => p.name, (str: string) => str.length, (x: number) => x % 2 == 0)
const isRemyEven = isNameOfEvenLength({ name: "Remy" })
// Hovering over isRemyEven shows us that it's a boolean value.
```
In case you had trouble reading some of the above, these articles explain most of the type-level features used in the above program:
- [Never type](https://www.typescriptlang.org/docs/handbook/basic-types.html#never)
- [Condtional types](https://www.typescriptlang.org/docs/handbook/advanced-types.html#conditional-types)
- [Type inference in conditional types](https://www.typescriptlang.org/docs/handbook/advanced-types.html#type-inference-in-conditional-types)
- [Variadic tuple types](https://www.typescriptlang.org/docs/handbook/release-notes/typescript-4-0.html#variadic-tuple-types)
- [Parameters type](https://www.typescriptlang.org/docs/handbook/utility-types.html#parameterstype)
- [ReturnType type](https://www.typescriptlang.org/docs/handbook/utility-types.html#returntypetype)

Comparing the above two programs, we can see that the TypeScript version has a significant amount of type annotations and these annotations require the reader to understand complex type-specific language features. The Typeless version requires using test-driven development, but the programmer might have done that even if they were not using Typeless. 

Comparing TypeScript and Typeless is complicated, and the above example is just one example. However, we hope to have convinced the reader that while types have big benefits, they also come at a cost. A further comparison of the two approaches is found [here](#why-use-javascript-and-typeless-when-i-can-use-typescript). We discuss what Typeless adds on top of existing JavaScript tooling [here](#why-not-use-the-javascript-support-in-typescripts-lsp-server).

<!-- Pipe works better for Typeless than compose, since when writing pipe can already execute the function that executes first, since it's supplied first. -->

### Features
Editor tooling provided by Typeless includes:

- Inline syntax and semantic errors
- Code completion, variable hover tooltips and function call help
- Code navigation such as go to definition and find references
- Assisted rename refactoring

### How do I get it?
Typeless is currently in the design phase and can't be used. If you're interested in using it, please star the GitHub page or upvote the newspost that brought you here. If you want to comment on the Typeless specification and have no please to do so, please leave your comment in a GitHub issue. You can also create a pull request to suggest changes.

# How do I use Typeless?
Typeless runs unit tests to learn things about the source code under test, so without tests Typeless is useless. If a programmer wants Typeless support while they're developing their code, they should use test-driven development. Once written any code with good test coverage will get support from Typeless, whether it was written in a test-driven approach or not. Typeless recognizes tests by looking for parameterless functions whose name ends in 'Test'. Here's an example:

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
  assert(fibonnaci(2) == 3)
  assert(fibonacci(3) == 5)
}

function fibonacci(n) {
  if (n < 2) return 1;
  return fibonacci(n-1) + fibonnacci(n-2);
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
  // After the opening paranthesis, Typeless will suggest calling fibonacci with either 2 or 3.
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

When a variable is reassigned the definition location is copied from the current value to the new one.

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

# FAQ

## Why use JavaScript and Typeless when I can use TypeScript?
Languages with type systems often provide some level of safety without asking the programmer to provide any type annotations. One scenario is when the programmer uses code from a library that already has type annotations, for example when using the `+` operator that's part of the JavaScript specification:
```typescript
function doesNotCompile() {
  return true + 3;
         ^^^^^^^^
  // Operator '+' cannot be applied to types 'boolean' and 'number'.
} 
```

Another situation in which programmers get safety for free is when type inference is performed, for example:
```typescript
function doesNotCompile() {
  var person = { name: 'Remy' }
  person.age
         ^^^
  // Property 'age' does not exist on type '{ name: string; }'
}
```

However, type inference only works for part of the code, and the programmer has to write type annotations where it doesn't or otherwise risk losing the safety provided by types. For TypeScript, type annotations should be provided on all function signatures since there is no type inference on them.

As TypeScript applications get more complex so do the types required to describe them. The TypeScript handbook features a section called [Advanced types](https://www.typescriptlang.org/docs/handbook/advanced-types.html), which indeed can be used to write advanced types. Here's an example:

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

## I write types to design my program. Does that mean Typeless is not for me?

Defining what data structures your program will work with is an important step in the development process. In Typeless, you can define your data structures by writing their constructors.

Consider the following TypeScript program:

```typescript
interface List<T> { }
class Node<T> extends List<T> {
  head: T;
  tail: List<T>;

  constructor(head: T, tail: List<T>) {
    this.head = head;
    this.tail = tail;
  }
}
class Nil extends List<never> {
}
const nil = new Nil();
```

And the Typeless equivalent:

```javascript
function NodeConstructorTest() {
  new Node(3, new Node("hello", nil));
}

class Node {
  constructor(head, tail) {
    this.head = head;
    this.tail = tail;
  }
}

const nil = {};
```

Note the Typeless program uses fewer concepts: there are no generics, no interfaces, no class inheritance and no `never` type. However, the Typeless program is more ambiguous about what values may be passed to the Node constructor. A seasoned Typeless programmer may opt to use generators to remove that ambiguity by writing the following test:

```javascript
function NodeConstructorTest() {
  let listGenerator;
  const nodeGenerator = generators.new(() => new Node(generators.value.pop(), listGenerator.pop()));
  listGenerator = generators.any(nil, nodeGenerator)
  nodeGenerator.pop();
}
```

The above test requires knowledge of generators. We believe using generators to define data structures has two advantages over using types:
- It requires fewer concepts.
- Generators can also be used to write powerful tests.

We can showcase the last argument by adding a `length` method to the Node class and writing a test for it:

```javascript
function NodeLengthTest() {
  const tail = listGenerator.pop();
  const node = new Node(undefined, tail);
  assert(node.length, tail.length + 1)
}

class Node {
  constructor(head, tail) { .. }

  length() { return 1 + tail.length }
}

const nil = { 
  length: () => 0;
};
```

## Why not use the JavaScript support in TypeScript's LSP server instead of Typeless?
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
  // After typing the dot, no semantic code completion is provided. 
  // There is only textual code completion based on what other identifiers occur in this file, 
  // for example 'person' is in the list.
}
```

With type inference only working in parts of the program, type-based JavaScript editor tooling is not able to match TypeScript editor tooling.

## Are there editor tooling features that Typeless does not implement?

There are particular programming concepts that only occur in typed languages, so there is no meaningful editor tooling that Typescript can implement for these.

The following editor tooling features provided by the LSP protocol are not implemented:
- textdocument/implementation and textdocument/declaration do not apply since Typeless does not have abstract or virtual functions

<!--  
References:
Exercise in using advanced types: https://manrueda.dev/blog/typescript-complex-types/
TypeScript advanced types manual: https://www.typescriptlang.org/docs/handbook/advanced-types.html
Last function in type world: https://stackoverflow.com/questions/56368755/typescript-get-the-type-of-the-last-parameter-of-a-function-type
Compose type: https://minaluke.medium.com/typescript-compose-function-b7512a7cc012
-->
