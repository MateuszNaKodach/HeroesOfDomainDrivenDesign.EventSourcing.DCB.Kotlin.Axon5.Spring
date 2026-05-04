package com.dddheroes.heroesofddd.creaturerecruitment.process

import com.dddheroes.heroesofddd.armies.write.addcreaturetoarmy.AddCreatureToArmy
import com.dddheroes.heroesofddd.creaturerecruitment.write.increaseavailablecreatures.IncreaseAvailableCreatures
import com.dddheroes.heroesofddd.resourcespool.write.depositresources.DepositResources
import com.dddheroes.heroesofddd.resourcespool.write.withdrawresources.WithdrawResources
import com.dddheroes.heroesofddd.shared.application.GameMetadata
import com.dddheroes.heroesofddd.shared.domain.identifiers.ArmyId
import com.dddheroes.heroesofddd.shared.domain.identifiers.CreatureId
import com.dddheroes.heroesofddd.shared.domain.identifiers.DwellingId
import com.dddheroes.heroesofddd.shared.domain.identifiers.ResourcesPoolId
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Quantity
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Resources
import com.dddheroes.sdk.application.CommandHandlerResult
import io.axoniq.workflow.dsl.kotlin.Kontext
import io.axoniq.workflow.runtime.api.annotation.Workflow
import org.axonframework.messaging.commandhandling.gateway.CommandDispatcher
import org.axonframework.messaging.core.interception.CorrelationDataInterceptor
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import kotlin.time.Duration.Companion.seconds

@ConditionalOnProperty(prefix = "processes", name = ["creaturerecruitmentprocess.enabled"])
@Component
class CreatureRecruitmentProcess {

    @Workflow(
        startOnEvent = "CreatureRecruitment.CreatureRecruited",
        idProperty = "dwellingId",
        workflowName = "CreatureRecruitmentProcess"
    )
    fun Kontext.onExecute() {
        val dwellingId = DwellingId(payload["dwellingId"] as String)
        val armyId = ArmyId(payload["toArmy"] as String)
        val creatureId = CreatureId(payload["creatureId"] as String)
        val quantity = Quantity(payload["quantity"] as Int)
        @Suppress("UNCHECKED_CAST")
        val totalCost = Resources.of((payload["totalCost"] as Map<String, Any?>)["raw"] as Map<String, Int>)

        // Step 1: Withdraw resources. Failure ends the workflow — nothing to compensate yet.
        try {
            block {
                execute("withdrawResources", { pc, _ ->
                    val pool = ResourcesPoolId(pc.getResource(CorrelationDataInterceptor.CORRELATION_DATA)!![GameMetadata.PLAYER_ID_KEY]!!)
                    CommandDispatcher.forContext(pc)
                        .send(WithdrawResources(pool, totalCost), CommandHandlerResult::class.java)
                        .join()
                        .throwIfFailure()
                    mapOf()
                }, timeout = 5.seconds)
            }
        } catch (e: Exception) {
            fail(e)
        }

        // Step 2: Add creature to army. On failure — restore stock and refund resources.
        try {
            block {
                execute("addCreatureToArmy", { pc, _ ->
                    CommandDispatcher.forContext(pc)
                        .send(AddCreatureToArmy(armyId, creatureId, quantity), CommandHandlerResult::class.java)
                        .join()
                        .throwIfFailure()
                    mapOf()
                }, timeout = 5.seconds)
            }
        } catch (e: Exception) {
            block {
                execute("compensateAvailableCreatures", { pc, _ ->
                    CommandDispatcher.forContext(pc)
                        .send(IncreaseAvailableCreatures(dwellingId, creatureId, quantity), CommandHandlerResult::class.java)
                        .join()
                        .throwIfFailure()
                    mapOf()
                }, timeout = 5.seconds)
            }
            block {
                execute("compensateDepositResources", { pc, _ ->
                    val pool = ResourcesPoolId(pc.getResource(CorrelationDataInterceptor.CORRELATION_DATA)!![GameMetadata.PLAYER_ID_KEY]!!)
                    CommandDispatcher.forContext(pc)
                        .send(DepositResources(pool, totalCost), CommandHandlerResult::class.java)
                        .join()
                        .throwIfFailure()
                    mapOf()
                }, timeout = 5.seconds)
            }
            fail(e)
        }
    }
}
