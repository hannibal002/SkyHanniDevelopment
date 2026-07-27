package skyhanni.plugin.areas.event

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tests for [HandleEventInspection].
 *
 * Covers the three diagnostic messages the inspection can emit and the
 * visibility-inference fix for override functions whose visibility is
 * inherited from an internal abstract declaration.
 */
class HandleEventInspectionTest : BasePlatformTestCase() {

    private val MISSING_ANNOTATION_PREFIX = "Function seems to handle"
    private val SHOULD_BE_PRIVATE = "Event handler function should be private"
    private val SHOULD_NOT_BE_ANNOTATED = "Function should not be annotated with @HandleEvent if it does not take a SkyHanniEvent"
    private val PRIMARY_NAME_MATCH_PREFIX = "Function name matches @PrimaryFunction of"
    private val INSPECTION_MESSAGES = setOf(SHOULD_BE_PRIVATE, SHOULD_NOT_BE_ANNOTATED)

    private fun addEventBase() {
        myFixture.addFileToProject(
            "at/hannibal2/skyhanni/api/event/SkyHanniEvent.kt",
            """
            package at.hannibal2.skyhanni.api.event
            import kotlin.reflect.KClass
            open class SkyHanniEvent
            annotation class HandleEvent(val eventType: KClass<*> = SkyHanniEvent::class)
            annotation class PrimaryFunction(val value: String)
            """.trimIndent(),
        )
    }

    private fun addFooEvent() {
        myFixture.addFileToProject(
            "com/example/FooEvent.kt",
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.SkyHanniEvent
            class FooEvent : SkyHanniEvent()
            """.trimIndent(),
        )
    }

    private fun addFooPrimaryEvent() {
        myFixture.addFileToProject(
            "com/example/FooPrimaryEvent.kt",
            """
              package com.example
              import at.hannibal2.skyhanni.api.event.SkyHanniEvent
              import at.hannibal2.skyhanni.api.event.PrimaryFunction
              @PrimaryFunction("onFooPrimary")
              class FooPrimaryEvent : SkyHanniEvent()
              """.trimIndent(),
        )
    }

    /** Runs the inspection on [code] and returns only the messages this inspection can emit. */
    private fun inspect(code: String): List<String> {
        myFixture.enableInspections(HandleEventInspection::class.java)
        myFixture.configureByText("Test.kt", code.trimIndent())
        return myFixture.doHighlighting()
            .mapNotNull { it.description }
            .filter {
                it in INSPECTION_MESSAGES ||
                    it.startsWith(PRIMARY_NAME_MATCH_PREFIX) ||
                    it.startsWith(MISSING_ANNOTATION_PREFIX)
            }
    }

    fun testOverrideWithEventReceiverAndNoExplicitVisibilityDoesNotWarn() {
        // Core regression: override inherits `internal` from the abstract declaration.
        // PSI sees no explicit modifier and reports isPublic=true, but the function is
        // not truly public. The inspection must not flag it.
        addEventBase()
        addFooEvent()
        val warnings = inspect(
            """
            package com.example
            abstract class AbstractTracker<T> {
                internal abstract fun T.handle(): Boolean
            }
            object ConcreteTracker : AbstractTracker<FooEvent>() {
                override fun FooEvent.handle(): Boolean = true
            }
        """,
        )
        assertFalse(warnings.any { it.startsWith(MISSING_ANNOTATION_PREFIX) })
    }

    fun testOverrideWithEventParamAndNoExplicitVisibilityDoesNotWarn() {
        addEventBase()
        addFooEvent()
        val warnings = inspect(
            """
            package com.example
            abstract class AbstractModule {
                internal abstract fun handle(event: FooEvent)
            }
            object ConcreteModule : AbstractModule() {
                override fun handle(event: FooEvent) {}
            }
        """,
        )
        assertFalse(warnings.any { it.startsWith(MISSING_ANNOTATION_PREFIX) })
    }

    fun testExplicitPublicOverrideWithEventReceiverWarns() {
        // An override that explicitly declares `public` is truly public and should be flagged.
        addEventBase()
        addFooEvent()
        val warnings = inspect(
            """
            package com.example
            abstract class AbstractTracker<T> {
                abstract fun T.handle(): Boolean
            }
            object ConcreteTracker : AbstractTracker<FooEvent>() {
                public override fun FooEvent.handle(): Boolean = true
            }
        """,
        )
        assertTrue(warnings.any { it.startsWith(MISSING_ANNOTATION_PREFIX) })
    }

    fun testExplicitPublicOverrideWithHandleEventDoesNotWarn() {
        addEventBase()
        addFooEvent()
        val warnings = inspect(
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.HandleEvent
            abstract class AbstractTracker<T> {
                abstract fun T.handle(): Boolean
            }
            object ConcreteTracker : AbstractTracker<FooEvent>() {
                @HandleEvent
                public override fun FooEvent.handle(): Boolean = true
            }
        """,
        )
        assertFalse(warnings.any { it.startsWith(MISSING_ANNOTATION_PREFIX) })
    }

    fun testPublicFunctionWithEventParamWarns() {
        addEventBase()
        addFooEvent()
        val warnings = inspect(
            """
            package com.example
            object MyModule {
                fun onFoo(event: FooEvent) {}
            }
        """,
        )
        assertTrue(warnings.any { it.startsWith(MISSING_ANNOTATION_PREFIX) })
    }

    fun testPublicFunctionWithEventReceiverWarns() {
        addEventBase()
        addFooEvent()
        val warnings = inspect(
            """
            package com.example
            object MyModule {
                fun FooEvent.handle() {}
            }
        """,
        )
        assertTrue(warnings.any { it.startsWith(MISSING_ANNOTATION_PREFIX) })
    }

    fun testPrivateFunctionWithEventReceiverDoesNotWarn() {
        addEventBase()
        addFooEvent()
        val warnings = inspect(
            """
              package com.example
              object MyModule {
                  private fun FooEvent.handle() {}
              }
          """,
        )
        assertFalse(warnings.any { it.startsWith(MISSING_ANNOTATION_PREFIX) })
    }

    fun testAnnotatedFunctionDoesNotWarn() {
        addEventBase()
        addFooEvent()
        val warnings = inspect(
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.HandleEvent
            object MyModule {
                @HandleEvent
                fun onFoo(event: FooEvent) {}
            }
        """,
        )
        assertFalse(warnings.any { it.startsWith(MISSING_ANNOTATION_PREFIX) })
    }

    fun testInternalFunctionWithEventParamWarns() {
        addEventBase()
        addFooEvent()
        val warnings = inspect(
            """
              package com.example
              object MyModule {
                  internal fun onFoo(event: FooEvent) {}
              }
          """,
        )
        assertTrue(warnings.any { it.startsWith(MISSING_ANNOTATION_PREFIX) })
    }

    fun testPrivateFunctionWithEventParamWarns() {
        addEventBase()
        addFooEvent()
        val warnings = inspect(
            """
              package com.example
              object MyModule {
                  private fun onFoo(event: FooEvent) {}
              }
          """,
        )
        assertTrue(warnings.any { it.startsWith(MISSING_ANNOTATION_PREFIX) })
    }

    fun testOpenFunctionWithEventParamDoesNotWarn() {
        addEventBase()
        addFooEvent()
        val warnings = inspect(
            """
            package com.example
            object MyModule {
                open fun onFoo(event: FooEvent) {}
            }
        """,
        )
        assertFalse(warnings.any { it.startsWith(MISSING_ANNOTATION_PREFIX) })
    }

    fun testHandleEventOnOverrideDoesNotWarnShouldBePrivate() {
        // Override visibility is constrained by the parent declaration; no warning expected.
        addEventBase()
        addFooEvent()
        val warnings = inspect(
            """
              package com.example
              import at.hannibal2.skyhanni.api.event.HandleEvent
              abstract class AbstractModule {
                  open fun handle(event: FooEvent) {}
              }
              object ConcreteModule : AbstractModule() {
                  @HandleEvent
                  override fun handle(event: FooEvent) = super.handle(event)
              }
          """,
        )
        assertFalse(SHOULD_BE_PRIVATE in warnings)
    }

    fun testHandleEventWithExplicitEventTypeOnInternalFunctionWarnsShouldBePrivate() {
        addEventBase()
        addFooEvent()
        val warnings = inspect(
            """
              package com.example
              import at.hannibal2.skyhanni.api.event.HandleEvent
              object MyModule {
                  @HandleEvent(eventType = FooEvent::class)
                  internal fun doSomething() {}
              }
          """,
        )
        assertTrue(SHOULD_BE_PRIVATE in warnings)
    }

    fun testPublicHandleEventFunctionWarnsShouldBePrivate() {
        addEventBase()
        addFooEvent()
        val warnings = inspect(
            """
              package com.example
              import at.hannibal2.skyhanni.api.event.HandleEvent
              object MyModule {
                  @HandleEvent
                  fun onFoo(event: FooEvent) {}
              }
          """,
        )
        assertTrue(SHOULD_BE_PRIVATE in warnings)
    }

    fun testPrivateHandleEventFunctionDoesNotWarnShouldBePrivate() {
        addEventBase()
        addFooEvent()
        val warnings = inspect(
            """
              package com.example
              import at.hannibal2.skyhanni.api.event.HandleEvent
              object MyModule {
                  @HandleEvent
                  private fun onFoo(event: FooEvent) {}
              }
          """,
        )
        assertFalse(SHOULD_BE_PRIVATE in warnings)
    }

    fun testPrimaryFunctionNameWithoutAnnotationWarns() {
        addEventBase()
        addFooPrimaryEvent()
        val warnings = inspect(
            """
              package com.example
              object MyModule {
                  fun onFooPrimary() {}
              }
          """,
        )
        assertTrue(warnings.any { it.startsWith(PRIMARY_NAME_MATCH_PREFIX) })
    }

    fun testPrimaryFunctionNameWithAnnotationDoesNotWarn() {
        addEventBase()
        addFooPrimaryEvent()
        val warnings = inspect(
            """
              package com.example
              import at.hannibal2.skyhanni.api.event.HandleEvent
              object MyModule {
                  @HandleEvent
                  fun onFooPrimary() {}
              }
          """,
        )
        assertFalse(warnings.any { it.startsWith(PRIMARY_NAME_MATCH_PREFIX) })
    }

    fun testHandleEventOnNonEventFunctionWarns() {
        addEventBase()
        val warnings = inspect(
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.HandleEvent
            object MyModule {
                @HandleEvent
                fun doSomething(x: String) {}
            }
        """,
        )
        assertTrue(SHOULD_NOT_BE_ANNOTATED in warnings)
    }
}
