package typeless.interpreter

import typeless.ast.Lambda

class FunctionCorrectness(functionsWithTests: Map[Lambda, Closure], tests: Set[Lambda]) {
  var functionCorrectness = Map.empty[Lambda, TrustLevel]

  def getLambdaTrustLevel(context: Context, lambda: Lambda): TrustLevel = {
    if (tests.contains(lambda))
      return Test

    functionCorrectness.get(lambda) match {
      case Some(correct) => correct
      case None =>
        val testOption = functionsWithTests.get(lambda)
        testOption.fold[TrustLevel](Untrusted)(test => {
          if (context.isRunningTest(test)) {
            Untrusted
          } else {
            val testResult = context.withFreshCallStack().runTest(test)
            val testPassed = testResult match {
              case _: ExceptionResult => Untrusted
              case _ => Trusted
            }
            functionCorrectness += lambda -> testPassed
            testPassed
          }
        })
    }
  }
}
