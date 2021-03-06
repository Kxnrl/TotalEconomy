/*
 * This file is part of Total Economy, licensed under the MIT License (MIT).
 *
 * Copyright (c) Eric Grandt <https://www.ericgrandt.com>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.erigitic.commands;

import com.erigitic.config.TEAccount;
import com.erigitic.main.TotalEconomy;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.cause.EventContext;
import org.spongepowered.api.service.economy.Currency;
import org.spongepowered.api.service.economy.transaction.ResultType;
import org.spongepowered.api.service.economy.transaction.TransactionResult;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;

public class AdminPayCommand implements CommandExecutor {

    public static CommandSpec commandSpec() {
        return CommandSpec.builder()
                .description(Text.of("Pay a player without removing money from your balance."))
                .permission("totaleconomy.command.adminpay")
                .executor(new AdminPayCommand())
                .arguments(
                        GenericArguments.user(Text.of("player")),
                        GenericArguments.string(Text.of("amount")),
                        GenericArguments.optional(GenericArguments.string(Text.of("currencyName")))
                ).build();
    }

    @Override
    public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
        String amountStr = (String) args.getOne("amount").get();
        User recipient = args.<User>getOne("player").get();
        Optional<String> optCurrencyName = args.getOne("currencyName");

        Pattern amountPattern = Pattern.compile("^[+-]?(\\d*\\.)?\\d+$");
        Matcher m = amountPattern.matcher(amountStr);

        if (m.matches()) {
            BigDecimal amount = new BigDecimal((String) args.getOne("amount").get()).setScale(2, BigDecimal.ROUND_DOWN);
            TEAccount recipientAccount = (TEAccount) TotalEconomy.getTotalEconomy().getAccountManager().getOrCreateAccount(recipient.getUniqueId()).get();
            TransactionResult transactionResult = getTransactionResult(recipientAccount, amount, optCurrencyName);

            if (transactionResult.getResult() == ResultType.SUCCESS) {
                Text amountText = Text.of(transactionResult.getCurrency().format(amount).toPlain().replace("-", ""));
                Map<String, String> messageValues = new HashMap<>();
                messageValues.put("sender", src.getName());
                messageValues.put("recipient", recipient.getName());
                messageValues.put("amount", amountText.toPlain());

                if (!amountStr.contains("-")) {
                    src.sendMessage(TotalEconomy.getTotalEconomy().getMessageManager().getMessage("command.adminpay.send.sender", messageValues));

                    if (recipient.isOnline()) {
                        recipient.getPlayer().get().sendMessage(TotalEconomy.getTotalEconomy().getMessageManager().getMessage("command.adminpay.send.recipient", messageValues));
                    }
                } else {
                    src.sendMessage(TotalEconomy.getTotalEconomy().getMessageManager().getMessage("command.adminpay.remove.sender", messageValues));

                    if (recipient.isOnline()) {
                        recipient.getPlayer().get().sendMessage(TotalEconomy.getTotalEconomy().getMessageManager().getMessage("command.adminpay.remove.recipient", messageValues));
                    }
                }

                return CommandResult.success();
            } else {
                throw new CommandException(Text.of("[TE] An error occurred while paying a player!"));
            }
        } else {
            throw new CommandException(Text.of("[TE] Invalid amount! Must be a number!"));
        }
    }

    /**
     * Retrieves the transaction result of the admin pay command.
     *
     * @param recipientAccount The account
     * @param amount The amount involved in the transaction
     * @param optCurrencyName The currency name
     * @return TransactionResult Result of the transaction
     * @throws CommandException Thrown when the specified currency does not exist
     */
    private TransactionResult getTransactionResult(TEAccount recipientAccount, BigDecimal amount, Optional<String> optCurrencyName) throws CommandException {
        Cause cause = Cause.builder()
                .append(TotalEconomy.getTotalEconomy().getPluginContainer())
                .build(EventContext.empty());

        if (optCurrencyName.isPresent()) {
            Optional<Currency> optCurrency = TotalEconomy.getTotalEconomy().getTECurrencyRegistryModule().getById("totaleconomy:" + optCurrencyName.get().toLowerCase());

            if (optCurrency.isPresent()) {
                return recipientAccount.deposit(optCurrency.get(), amount, cause);
            } else {
                throw new CommandException(Text.of(TextColors.RED, "[TE] The specified currency does not exist!"));
            }
        } else {
            return recipientAccount.deposit(TotalEconomy.getTotalEconomy().getDefaultCurrency(), amount, cause);
        }
    }
}
