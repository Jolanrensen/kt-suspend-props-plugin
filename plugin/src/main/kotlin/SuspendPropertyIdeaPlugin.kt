package nl.jolanrensen.suspendPropertyPlugin

import arrow.meta.Meta
import arrow.meta.MetaPlugin
import arrow.meta.ide.IdeMetaPlugin
import arrow.meta.ide.IdePlugin
import arrow.meta.ide.invoke
import arrow.meta.ide.phases.IdeContext
import kotlin.contracts.ExperimentalContracts


//class SuspendPropertyIdeaPlugin : Meta {
//
//    @ExperimentalContracts
//    override fun intercept(ctx: IdeContext): List<IdePlugin> = super.intercept(ctx)
//}

val IdeMetaPlugin.suspendPropertyIde: arrow.meta.ide.IdePlugin
    get() = "SuspendProperty" {

        meta(

        )
    }