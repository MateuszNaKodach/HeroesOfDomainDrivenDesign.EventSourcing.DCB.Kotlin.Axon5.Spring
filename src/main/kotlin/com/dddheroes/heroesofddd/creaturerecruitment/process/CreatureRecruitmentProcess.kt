package com.dddheroes.heroesofddd.creaturerecruitment.process

import com.dddheroes.heroesofddd.armies.write.addcreaturetoarmy.AddCreatureToArmy
import com.dddheroes.heroesofddd.creaturerecruitment.write.increaseavailablecreatures.IncreaseAvailableCreatures
import com.dddheroes.heroesofddd.creaturerecruitment.write.recruitcreature.RecruitCreature
import com.dddheroes.heroesofddd.resourcespool.write.depositresources.DepositResources
import com.dddheroes.heroesofddd.resourcespool.write.withdrawresources.WithdrawResources
import com.dddheroes.heroesofddd.shared.domain.identifiers.ArmyId
import com.dddheroes.heroesofddd.shared.domain.identifiers.CreatureId
import com.dddheroes.heroesofddd.shared.domain.identifiers.DwellingId
import com.dddheroes.heroesofddd.shared.domain.identifiers.ResourcesPoolId
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Quantity
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Resources
import com.dddheroes.sdk.application.CommandHandlerResult
import io.axoniq.workflow.dsl.kotlin.Kontext
import io.axoniq.workflow.runtime.api.annotation.Workflow
import org.axonframework.messaging.commandhandling.gateway.CommandGateway
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import kotlin.time.Duration.Companion.seconds

@ConditionalOnProperty(prefix = "processes", name = ["creaturerecruitmentprocess.enabled"])
@Component
class CreatureRecruitmentProcess(private val commandGateway: CommandGateway) {

    @Workflow(
        startOnEvent = "CreatureRecruitment.CreatureRecruitmentRequested",
        idProperty = "recruitmentId",
        workflowName = "CreatureRecruitmentProcess"
    )
    fun Kontext.onExecute() {
        val dwellingId = DwellingId(payload["dwellingId"] as String)
        val pool = ResourcesPoolId(payload["resourcesPoolId"] as String)
        val armyId = ArmyId(payload["armyId"] as String)
        val creatureId = CreatureId(payload["creatureId"] as String)
        val quantity = Quantity(payload["quantity"] as Int)
        @Suppress("UNCHECKED_CAST")
        val expectedCost = Resources.of(payload["expectedCost"] as Map<String, Int>)
        // gameId/playerId ride on metadata via SimpleCorrelationDataProvider — Axon copies them
        // from the workflow's inbound event onto every command dispatched inside awaitExecute.

        // Step 1 — Withdraw resources. Failure ends the workflow; nothing to compensate.
        try {
            awaitExecute("withdrawResources", { commandGateway.send(WithdrawResources(pool, expectedCost))
                .resultAs(CommandHandlerResult::class.java).join() }, timeout = 30.seconds)
        } catch (e: Exception) {
            fail(e)
            return
        }

        // Step 2 — Recruit creature (non-DCB slice; emits CreatureRecruited only).
        try {
            awaitExecute("recruitCreature", { commandGateway.send(
                RecruitCreature(dwellingId, creatureId, armyId, quantity, expectedCost)
            ).resultAs(CommandHandlerResult::class.java).join() }, timeout = 30.seconds)
        } catch (e: Exception) {
            awaitExecute("compensateRefundAfterRecruitFailure", {
                commandGateway.send(DepositResources(pool, expectedCost))
                    .resultAs(CommandHandlerResult::class.java).join()
            }, timeout = 30.seconds)
            fail(e)
            return
        }

        // Step 3 — Add the recruited stack to the army (separate aggregate).
        try {
            awaitExecute("addCreatureToArmy", { commandGateway.send(AddCreatureToArmy(armyId, creatureId, quantity))
                .resultAs(CommandHandlerResult::class.java).join() }, timeout = 30.seconds)
        } catch (e: Exception) {
            awaitExecute("compensateAvailableCreatures", {
                commandGateway.send(IncreaseAvailableCreatures(dwellingId, creatureId, quantity))
                    .resultAs(CommandHandlerResult::class.java).join()
            }, timeout = 30.seconds)
            awaitExecute("compensateRefundAfterArmyFailure", {
                commandGateway.send(DepositResources(pool, expectedCost))
                    .resultAs(CommandHandlerResult::class.java).join()
            }, timeout = 30.seconds)
            fail(e)
        }
    }
}

fun CreatureRecruitmentProcess.execute(kontext: Kontext) = with(kontext) { onExecute() }
