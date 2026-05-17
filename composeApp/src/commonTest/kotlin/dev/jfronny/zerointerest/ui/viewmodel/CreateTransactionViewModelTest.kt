package dev.jfronny.zerointerest.ui.viewmodel

import dev.jfronny.zerointerest.service.CoreServicesTest
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import org.koin.core.parameter.parametersOf
import org.koin.test.inject

class CreateTransactionViewModelTest : CoreServicesTest() {
    init {
        test("initial state should have correct default sender") {
            val viewModel by inject<CreateTransactionViewModel> {
                parametersOf(roomId, null)
            }
            viewModel.state.value.sender shouldBe testUser
        }

        test("description changes update state") {
            val viewModel by inject<CreateTransactionViewModel> {
                parametersOf(roomId, null)
            }
            viewModel.onDescriptionChanged("Pizza")
            viewModel.state.value.description shouldBe "Pizza"
        }

        test("sender changes update state") {
            val viewModel by inject<CreateTransactionViewModel> {
                parametersOf(roomId, null)
            }
            viewModel.onSenderChanged(alice)
            viewModel.state.value.sender shouldBe alice
        }

        test("total amount changes distribute to recipients") {
            val viewModel by inject<CreateTransactionViewModel> {
                parametersOf(roomId, null)
            }
            viewModel.onRecipientsChanged(setOf(alice, bob))
            viewModel.onTotalChanged("10.00")

            val state = viewModel.state.value
            state.total.amountStr shouldBe "10.00"
            state.recipients.mapValues { it.value.amountStr } shouldContainExactly mapOf(
                alice to "5.00",
                bob to "5.00"
            )
        }

        test("total amount changes distribute with remainder") {
            val viewModel by inject<CreateTransactionViewModel> {
                parametersOf(roomId, null)
            }
            // Use a list to ensure stable order for distribute
            viewModel.onRecipientsChanged(linkedSetOf(alice, bob))
            viewModel.onTotalChanged("10.01")

            val state = viewModel.state.value
            state.total.amountStr shouldBe "10.01"
            state.recipients.mapValues { it.value.amountStr } shouldContainExactly mapOf(
                alice to "5.01",
                bob to "5.00"
            )
        }

        test("individual amount changes update total") {
            val viewModel by inject<CreateTransactionViewModel> {
                parametersOf(roomId, null)
            }
            viewModel.onRecipientsChanged(setOf(alice, bob))
            viewModel.onIndividualAmountChanged(alice, "3.00")
            viewModel.onIndividualAmountChanged(bob, "4.50")

            val state = viewModel.state.value
            state.total.amountStr shouldBe "7.50"
            state.recipients.mapValues { it.value.amountStr } shouldContainExactly mapOf(
                alice to "3.00",
                bob to "4.50"
            )
        }

        test("validation works for total amount") {
            val viewModel by inject<CreateTransactionViewModel> {
                parametersOf(roomId, null)
            }
            viewModel.onTotalChanged("invalid")
            val state = viewModel.state.first { it.total.amountStr == "invalid" }
            state.total.isValid shouldBe false
            
            viewModel.onTotalChanged("12.34")
            val stateValid = viewModel.state.first { it.total.amountStr == "12.34" && it.total.isValid }
            stateValid.total.isValid shouldBe true
        }

        test("submit calls transaction service and triggers onDone") {
            val viewModel by inject<CreateTransactionViewModel> {
                parametersOf(roomId, null)
            }
            var doneCalled = false
            
            viewModel.onDescriptionChanged("Lunch")
            viewModel.onRecipientsChanged(setOf(alice))
            viewModel.onTotalChanged("15.00")
            
            // Wait for all updates to settle
            viewModel.state.first { it.total.amountStr == "15.00" && it.allValid }
            
            viewModel.submit { doneCalled = true }

            kotlinx.coroutines.withTimeout(2000) {
                while(!doneCalled) {
                    kotlinx.coroutines.delay(10)
                }
            }
            doneCalled shouldBe true
        }
    }
}
