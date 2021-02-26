# Typeless: the benefits of TypeScript, without the types

Typeless provides great editor tooling for plain JavaScript. Typeless uses unit tests instead of type annotations to understand source code. Here's a demo of some Typeless features: 

![Features demo](https://github.com/keyboardDrummer/typeless/raw/master/vscode-extension/images/demo.gif)

You can see features you're used to from languages such as TypeScript:
- Inline syntax and semantic errors
- Code completion
- Hover tooltips over variables
- User written documentation is shown in the tooling
- Code navigation such as go to definition and find references
- Assisted rename refactoring

There are also features that you don't normally see:
- Inline errors for failed tests are shown not where the exception occurred, but where the bug likely is.
- There are no references to types anywhere, everything is shown using values.

For a comparison between TypeScript and Typeless, go [here](#why-use-javascript-and-typeless-when-i-can-use-typescript). For a comparison between JavaScript and Typeless, go [here](#why-not-use-the-javascript-support-in-typescripts-lsp-server).

<!-- Pipe works better for Typeless than compose, since when writing pipe can already execute the function that executes first, since it's supplied first. -->

### How do I get it?
Typeless is currently in the prototype phase. You can only use it by building it from source.

Steps to try Typeless:
- Make sure VSCode can be run from your path using `code`. More information is [here](https://stackoverflow.com/a/36882426/93197).
- Install the Mill build tool by following [these instructions](https://com-lihaoyi.github.io/mill/#installation). 
- Run `mill server.vscode` to start VSCode with the Typeless extension.
- Create a `.tl`, write some JavaScript and a function ending in `Test`.

Because Typeless depends on running tests, it's important to always write tests before writing the implementation, also called test-driven development, when using Typeless.

If you're interested in Typeless, please star the GitHub page or upvote the post that brought you here. Contributions are much appreciated.

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

As TypeScript applications get more complex, so do the types required to describe them. The TypeScript handbook features a section called [Advanced types](https://www.typescriptlang.org/docs/handbook/advanced-types.html). Here's an example of a program that uses advanced types to implement the `pipe` function, which takes multiple functions as an argument and pipes them all together, like UNIX pipes.

```typescript
type Person = { name: string }
function pipeTest() {
  const isNameOfEvenLength = pipe((person: Person) => 
    person.name, (str: string) => str.length, (x: number) => x % 2 == 0);
  assert(isNameOfEvenLength({ name: "Remy" }))
  assert(!isNameOfEvenLength({ name: "Elise" }))
}

type ArityOneFn = (arg: any) => any;
type PickLastInTuple<T extends any[]> = 
  T extends [...rest: infer U, argn: infer L ] ? L : never;

const pipe = <T extends ArityOneFn[]>(...fns: T) => 
  (p: Parameters<T[0]>[0]): ReturnType<PickLastInTuple<T>> => 
  fns.reduce((acc: any, cur: ArityOneFn) => cur(acc), p);
```

Note how the function `pipe` in the above program has only one line of implementation, but five lines to describe its type. Worse, even with all these types, the type checker will not guarantee that the functions passed to `pipe` all fit together.

In case you had trouble reading the above, these articles explain most of the type-level features used:
- [Never type](https://www.typescriptlang.org/docs/handbook/basic-types.html#never)
- [Condtional types](https://www.typescriptlang.org/docs/handbook/advanced-types.html#conditional-types)
- [Type inference in conditional types](https://www.typescriptlang.org/docs/handbook/advanced-types.html#type-inference-in-conditional-types)
- [Variadic tuple types](https://www.typescriptlang.org/docs/handbook/release-notes/typescript-4-0.html#variadic-tuple-types)
- [Parameters type](https://www.typescriptlang.org/docs/handbook/utility-types.html#parameterstype)
- [ReturnType type](https://www.typescriptlang.org/docs/handbook/utility-types.html#returntypetype)

The types in the above program have become a little program of their own, and understanding which values are part of this type requires mentally executing this type-level program. We want to offer programmers the opportunity to avoid doing such mental gymnastics and work with a type-free language.

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

The Typeless equivalent:

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

Since type inference only works in parts of the program, type-based JavaScript editor tooling is not able to match TypeScript editor tooling.


<!--  

## Are there editor tooling features that Typeless does not implement?

There are particular programming concepts that only occur in typed languages, so there is no meaningful editor tooling that Typescript can implement for these.

The following editor tooling features provided by the LSP protocol are not implemented:
- textdocument/implementation and textdocument/declaration do not apply since Typeless does not have abstract or virtual functions


References:
Exercise in using advanced types: https://manrueda.dev/blog/typescript-complex-types/
TypeScript advanced types manual: https://www.typescriptlang.org/docs/handbook/advanced-types.html
Last function in type world: https://stackoverflow.com/questions/56368755/typescript-get-the-type-of-the-last-parameter-of-a-function-type
Compose type: https://minaluke.medium.com/typescript-compose-function-b7512a7cc012
-->
