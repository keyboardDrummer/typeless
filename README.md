# typeless
A programming language that uses tests instead of type annotations to compile


- When a piece of code is not covered by a test, this is a compilation error.
- Overloaded method calls can be resolved by running tests and seeing with which overload the test does not have a runtime type errors.

- Can we skip types from structure definitions as well? Do we need to know how many bytes are needed for specific fields? Maybe we can figure that out from the tests, and the inserted constants.

- 'tested/api/public' functions need to have generators for each input argument. Generators are specified alongside the functions. 'tested' functions are the roots of the callgraph of your program, and every function either is a tested function or is (indirectly) called by a tested function.

### Editor tooling
- You can hover over functions parameters and you will get examples of what inputs values it can have and what output it returns. Tests are used to supply the example input/output values.
- Usages of definitions are discovered by running tests. This powers features such as 'goto definition/find references/rename'.
- How does code completion work? Take all functions in scope, look at what values they are called with, and whether any of the values you want to pass to this function are in that list.

How do we determine which functions can be called with particular variables as arguments?
  - Tests determine what values variables can contain. 
  - How do we determine, of all the definitions that are in scope, or can be imported, which can be applied to certain variables? 
  - I guess we can determine the type of a variable in terms of bytes..?
  - Can we, while we run the tests, assign applicable functions to values? Example, given the value "hello", we assign all the string functions to that value. Maybe the idea is to assign functions to constructors?
  
Constructors are the types of this language. Literal values are constructor calls. Datatypes are defined by defining constructors.
 
How do you write a SumInts function that takes an array of integers? How do you specify with + operator to use? Maybe it's not possible to write this function without automatically also making it work for any datatypes that have a binary + operator?

Maybe the problem of finding correct overloads should be solved by managing the amount of definitions that are imported. We could also have simple rules that disallow resolving overloads through testing when there are more than 10 options. In that case you can use a 'hide' keyword to remove definitions from the scope.


#### Data
You can encode data in functions by having a function take arguments, and return a higher order function that takes extractor functions as arguments.

```
structure Person: age, height, gender // TODO adds generators for fields.
```

#### Passing functions
Function can be passed as values, but maybe we also need a way to pass functions alongside other values or objects, something like dynamic-dispatch in OO, or prototypes from JavaScript, or Typeclasses from Haskell.

I'm most inclined to adding prototypes, but then it really becomes like JavaScript.


### Syntax
The syntax should optimize for writeability. The editor can add visualizations to make it more readable. We can add syntax that triggers a code writing 'wizard' that after completion rewrites the code to normal style. A problem with these wizards is that they're hard to discover because you won't see their syntax when reading existing code.

__Code completion for calls__
In OO languages, the . operator is used to refine the scope to what is exposed by the type of the target in front of the dot. We could generalize this mechanism, and say that given a number of arguments, we'd like to see the functions that can use these arguments. 

A problem with allowing multiple arguments to filter the code completion for the function name, is that you might have specified multiple arguments with similar types before invoking code completion and picking a function, but now the order in which to pass them the arguments is not clear. It might be simpler to only use a single argument as a filter for what function to call code completion on.

We can write an argument, then let . trigger code completion for a function name, and then after picking the function rewrite to the normal function calling style.

#### Haskel syntax
Can we take a Haskell like syntax? The arguments and the funciton name in a function call are separated by spaces. Per argument the parameter name must be specified, unless the two are equal. First comes the parameter name, then a comma, then the argument. The function name must be prefixed with 'call,'



Factorial:
```
factorial n = if n < 2
              then 1
              else n * factorial (n - 1)
```

#### Traditional syntax
Comma's are used for separators.

```
testBefriend {
  call=befriend, person
}
```

#### Minimal syntax

The syntax for calls is so that the order of the argument and of the called function name do not matter. 

```

befriend person = ???

person = createPerson()

testBefriend = call.befriend person 

testBefriend2 = person call.befriend
  

```



