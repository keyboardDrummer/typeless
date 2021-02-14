package typeless.interpreter

import typeless.ast.Lambda

class FunctionCorrectness(functionsWithTests: Map[Lambda, Closure]) {
  var functionCorrectness = Map.empty[Lambda, Boolean]

  def isLambdaCorrect(context: Context, lambda: Lambda): Boolean = {
    functionCorrectness.get(lambda) match {
      case Some(correct) => correct
      case None =>
        val testOption = functionsWithTests.get(lambda)
        testOption.fold(false)(test => {
          if (context.isRunningTest(test)) {
            false
          } else {
            val testResult = context.withFreshCallStack().runTest(test)
            val testPassed = testResult match {
              case _: ExceptionResult => false
              case _ => true
            }
            functionCorrectness += lambda -> testPassed
            testPassed
          }
        })
    }
  }
}
