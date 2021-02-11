package typeless.interpreter

class FunctionCorrectness(functionsWithTests: Map[Closure, Closure]) {
  var functionCorrectness = Map.empty[Closure, Boolean]

  def isClosureCorrect(context: Context, closure: Closure): Boolean = {
    functionCorrectness.get(closure) match {
      case Some(correct) => correct
      case None =>
        val testOption = functionsWithTests.get(closure)
        testOption.fold(false)(test => {
          if (context.isRunningTest(test)) {
            false
          } else {
            val testResult = context.withFreshCallStack().runTest(test)
            val testPassed = testResult match {
              case _: ExceptionResult => false
              case _ => true
            }
            functionCorrectness += closure -> testPassed
            testPassed
          }
        })
    }
  }
}
