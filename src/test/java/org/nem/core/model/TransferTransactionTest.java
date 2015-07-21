package org.nem.core.model;

import org.hamcrest.core.*;
import org.junit.*;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.nem.core.messages.*;
import org.nem.core.model.mosaic.*;
import org.nem.core.model.observers.*;
import org.nem.core.model.primitive.*;
import org.nem.core.serialization.*;
import org.nem.core.test.*;
import org.nem.core.time.TimeInstant;

import java.util.*;
import java.util.stream.*;

@RunWith(Enclosed.class)
public class TransferTransactionTest {

	public static class Main {
		@Test
		public void ctorCreatesTransferTransactionVersionTwoByDefault() {
			// Arrange:
			final Account signer = Utils.generateRandomAccount();
			final Account recipient = Utils.generateRandomAccount();

			// Act:
			final TransferTransaction transaction = createTransferTransaction(signer, recipient, 123);

			// Assert:
			Assert.assertThat(transaction.getEntityVersion(), IsEqual.equalTo(2));
		}

		@Test
		public void canCreateTransferTransactionWithDifferentVersionThanTwo() {
			// Arrange:
			final Account signer = Utils.generateRandomAccount();
			final Account recipient = Utils.generateRandomAccount();

			// Act:
			final TransferTransaction transaction1 = createTransferTransaction(1, signer, recipient, 123);
			final TransferTransaction transaction2 = createTransferTransaction(123, signer, recipient, 123);

			// Assert:
			Assert.assertThat(transaction1.getEntityVersion(), IsEqual.equalTo(1));
			Assert.assertThat(transaction2.getEntityVersion(), IsEqual.equalTo(123));
		}

		private static TransferTransaction createTransferTransaction(
				final Account sender,
				final Account recipient,
				final long amount) {
			return new TransferTransaction(TimeInstant.ZERO, sender, recipient, Amount.fromNem(amount), null);
		}

		private static TransferTransaction createTransferTransaction(
				final int version,
				final Account sender,
				final Account recipient,
				final long amount) {
			return new TransferTransaction(version, TimeInstant.ZERO, sender, recipient, Amount.fromNem(amount), null);
		}
	}

	private static abstract class AbstractTransferTransactionTest {

		//region createTransferTransaction

		protected abstract TransferTransaction createTransferTransaction(
				final Account sender,
				final Account recipient,
				final long amount,
				final TransferTransactionAttachment attachment);

		protected TransferTransaction createTransferTransaction(
				final Account sender,
				final Account recipient,
				final long amount,
				final Message message,
				Collection<MosaicTransferPair> smartTileBag) {
			smartTileBag = null == smartTileBag ? Collections.emptyList() : smartTileBag;
			final TransferTransactionAttachment attachment = new TransferTransactionAttachment(message);
			smartTileBag.forEach(attachment::addMosaicTransfer);
			return this.createTransferTransaction(sender, recipient, amount, attachment);
		}

		protected TransferTransaction createTransferTransaction(
				final Account sender,
				final Account recipient,
				final long amount,
				final Message message) {
			return this.createTransferTransaction(sender, recipient, amount, message, null);
		}

		//endregion

		//region basic

		//region constructor

		@Test(expected = IllegalArgumentException.class)
		public void recipientIsRequired() {
			// Arrange:
			final Account signer = Utils.generateRandomAccount();
			final Message message = new PlainMessage(new byte[] { 12, 50, 21 });

			// Act:
			this.createTransferTransaction(signer, null, 123, message);
		}

		@Test
		public void canCreateTransactionWithMessageAndWithoutSmartTiles() {
			// Arrange:
			final Account signer = Utils.generateRandomAccount();
			final Account recipient = Utils.generateRandomAccount();
			final Message message = new PlainMessage(new byte[] { 12, 50, 21 });

			// Act:
			final TransferTransaction transaction = this.createTransferTransaction(signer, recipient, 123, message);

			// Assert:
			assertTransactionFields(transaction, signer, recipient, Amount.fromNem(123L));
			Assert.assertThat(transaction.getMessage().getDecodedPayload(), IsEqual.equalTo(new byte[] { 12, 50, 21 }));
			Assert.assertThat(transaction.getMessageLength(), IsEqual.equalTo(3));
			Assert.assertThat(transaction.getAttachment().getMosaicTransfers().isEmpty(), IsEqual.equalTo(true));
		}

		@Test
		public void canCreateTransactionWithoutMessageAndWithoutSmartTiles() {
			// Arrange:
			final Account signer = Utils.generateRandomAccount();
			final Account recipient = Utils.generateRandomAccount();

			// Act:
			final TransferTransaction transaction = this.createTransferTransaction(signer, recipient, 123, (Message)null);

			// Assert:
			assertTransactionFields(transaction, signer, recipient, Amount.fromNem(123L));
			Assert.assertThat(transaction.getMessage(), IsNull.nullValue());
			Assert.assertThat(transaction.getMessageLength(), IsEqual.equalTo(0));
			Assert.assertThat(transaction.getAttachment().getMosaicTransfers().isEmpty(), IsEqual.equalTo(true));
		}

		@Test
		public void canCreateTransactionWithoutMessageAndWithSmartTiles() {
			// Arrange:
			final Account signer = Utils.generateRandomAccount();
			final Account recipient = Utils.generateRandomAccount();
			final Collection<MosaicTransferPair> pairs = createSmartTiles();

			// Act:
			final TransferTransaction transaction = this.createTransferTransaction(signer, recipient, 123, null, pairs);

			// Assert:
			assertTransactionFields(transaction, signer, recipient, Amount.fromNem(123L));
			Assert.assertThat(transaction.getAttachment().getMosaicTransfers(), IsEquivalent.equivalentTo(pairs));
		}

		@Test
		public void canCreateTransactionWithMessageAndWithSmartTiles() {
			// Arrange:
			final Account signer = Utils.generateRandomAccount();
			final Account recipient = Utils.generateRandomAccount();
			final Message message = new PlainMessage(new byte[] { 12, 50, 21 });
			final Collection<MosaicTransferPair> pairs = createSmartTiles();

			// Act:
			final TransferTransaction transaction = this.createTransferTransaction(signer, recipient, 123, message, pairs);

			// Assert:
			assertTransactionFields(transaction, signer, recipient, Amount.fromNem(123L));
			Assert.assertThat(transaction.getMessage().getDecodedPayload(), IsEqual.equalTo(new byte[] { 12, 50, 21 }));
			Assert.assertThat(transaction.getMessageLength(), IsEqual.equalTo(3));
			Assert.assertThat(transaction.getAttachment().getMosaicTransfers(), IsEquivalent.equivalentTo(pairs));
		}

		//endregion

		//region round trip

		@Test
		public void transactionCanBeRoundTrippedWithMessageAndWithoutSmartTiles() {
			// Arrange:
			this.assertCanBeRoundTripped(new byte[] { 12, 50, 21 }, null);
		}

		@Test
		public void transactionCanBeRoundTrippedWithoutMessageAndWithoutSmartTiles() {
			// Assert:
			this.assertCanBeRoundTripped(null, null);
		}

		//endregion

		protected void assertCanBeRoundTripped(final byte[] messageBytes, final Collection<MosaicTransferPair> pairs) {
			// Arrange:
			final Account signer = Utils.generateRandomAccount();
			final Account recipient = Utils.generateRandomAccountWithoutPrivateKey();
			final Message message = null == messageBytes ? null : new PlainMessage(messageBytes);
			final TransferTransaction originalTransaction = this.createTransferTransaction(signer, recipient, 123, message, pairs);

			final MockAccountLookup accountLookup = MockAccountLookup.createWithAccounts(signer, recipient);
			final TransferTransaction transaction = createRoundTrippedTransaction(originalTransaction, accountLookup);

			// Assert:
			assertTransactionFields(transaction, signer, recipient, Amount.fromNem(123L));
			Assert.assertThat(
					null == message ? transaction.getMessage() : transaction.getMessage().getDecodedPayload(),
					IsEqual.equalTo(null == message ? null : messageBytes));
			Assert.assertThat(
					transaction.getAttachment().getMosaicTransfers(),
					IsEquivalent.equivalentTo(null == pairs ? Collections.emptyList() : pairs));
		}

		private static void assertTransactionFields(
				final TransferTransaction transaction,
				final Account signer,
				final Account recipient,
				final Amount amount) {
			Assert.assertThat(transaction.getSigner(), IsEqual.equalTo(signer));
			Assert.assertThat(transaction.getDebtor(), IsEqual.equalTo(signer));
			Assert.assertThat(transaction.getRecipient(), IsEqual.equalTo(recipient));
			Assert.assertThat(transaction.getAmount(), IsEqual.equalTo(amount));
		}

		//endregion

		//region Message Length

		@Test
		public void messageLengthReturnsEncodedLength() {
			// Arrange:
			final TransferTransaction transaction = this.createTransactionWithMockMessage(100, 44);

			// Assert:
			Assert.assertThat(transaction.getMessageLength(), IsEqual.equalTo(100));
		}

		private TransferTransaction createTransactionWithMockMessage(final int encodedMessageSize, final int decodedMessageSize) {
			// Arrange:
			final Account signer = Utils.generateRandomAccount();
			final Account recipient = Utils.generateRandomAccount();
			final MockMessage message = new MockMessage(7);
			message.setEncodedPayload(new byte[encodedMessageSize]);
			message.setDecodedPayload(new byte[decodedMessageSize]);
			return this.createTransferTransaction(signer, recipient, 0, message);
		}

		//endregion

		//region Message set to null if payload length is 0

		@Test
		public void messageIsSetToNullIfPlainMessagePayloadIsNull() {
			// Assert:
			this.assertMessageFieldIsNull(null, MessageTypes.PLAIN, true);
		}

		@Test
		public void messageIsSetToNullIfPlainMessagePayloadLengthIsZero() {
			// Assert:
			this.assertMessageFieldIsNull(new byte[] {}, MessageTypes.PLAIN, true);
		}

		@Test
		public void messageIsNotSetToNullIfPlainMessagePayloadLengthIsNotZero() {
			// Assert:
			this.assertMessageFieldIsNull(new byte[] { 1 }, MessageTypes.PLAIN, false);
		}

		@Test
		public void messageIsSetToNullIfSecureMessagePayloadIsNull() {
			// Assert:
			this.assertMessageFieldIsNull(null, MessageTypes.SECURE, true);
		}

		@Test
		public void messageIsNotSetToNullIfSecureMessagePayloadLengthIsZero() {
			// Assert:
			this.assertMessageFieldIsNull(new byte[] {}, MessageTypes.SECURE, false);
		}

		@Test
		public void messageIsNotSetToNullIfSecureMessagePayloadLengthIsNotZero() {
			// Assert:
			this.assertMessageFieldIsNull(new byte[] { 1 }, MessageTypes.SECURE, false);
		}

		private void assertMessageFieldIsNull(final byte[] messageBytes, final int messageType, final boolean isNullMessageExpected) {
			// Arrange:
			final Account signer = Utils.generateRandomAccount();
			final Account recipient = Utils.generateRandomAccountWithoutPrivateKey();
			Message message = null;
			if (null != messageBytes) {
				message = MessageTypes.PLAIN == messageType
						? new PlainMessage(messageBytes)
						: SecureMessage.fromDecodedPayload(signer, recipient, messageBytes);
			}

			final TransferTransaction originalTransaction = this.createTransferTransaction(signer, recipient, 123, message);
			final MockAccountLookup accountLookup = MockAccountLookup.createWithAccounts(signer, recipient);
			final Deserializer deserializer = Utils.roundtripVerifiableEntity(originalTransaction, accountLookup);
			deserializer.readInt("type");

			// Act:
			final TransferTransaction transaction = new TransferTransaction(VerifiableEntity.DeserializationOptions.VERIFIABLE, deserializer);

			// Assert:
			Assert.assertThat(transaction.getMessage(), isNullMessageExpected ? IsNull.nullValue() : IsEqual.equalTo(message));
		}

		//endregion

		//region getAccounts

		@Test
		public void getAccountsIncludesSignerAndRecipientAccounts() {
			// Arrange:
			final Account signer = Utils.generateRandomAccount();
			final Account recipient = Utils.generateRandomAccount();
			final TransferTransaction transaction = this.createTransferTransaction(signer, recipient, 99, (Message)null);

			// Act:
			final Collection<Account> accounts = transaction.getAccounts();

			// Assert:
			Assert.assertThat(accounts, IsEquivalent.equivalentTo(signer, recipient));
		}

		//endregion

		//region execute

		@Test
		public void executeRaisesAppropriateNotificationsForXemTransfers() {
			// Arrange:
			final Account signer = Utils.generateRandomAccount();
			final Account recipient = Utils.generateRandomAccount();
			final TransferTransaction transaction = this.createTransferTransaction(signer, recipient, 99, (Message)null);
			transaction.setFee(Amount.fromNem(10));

			// Act:
			final TransactionObserver observer = Mockito.mock(TransactionObserver.class);
			transaction.execute(observer);

			// Assert:
			final ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
			Mockito.verify(observer, Mockito.times(3)).notify(notificationCaptor.capture());
			NotificationUtils.assertAccountNotification(notificationCaptor.getAllValues().get(0), recipient);
			NotificationUtils.assertBalanceTransferNotification(notificationCaptor.getAllValues().get(1), signer, recipient, Amount.fromNem(99));
			NotificationUtils.assertBalanceDebitNotification(notificationCaptor.getAllValues().get(2), signer, Amount.fromNem(10));
		}

		//endregion

		//region undo

		@Test
		public void undoRaisesAppropriateNotificationsForXemTransfers() {
			// Arrange:
			final Account signer = Utils.generateRandomAccount();
			final Account recipient = Utils.generateRandomAccount();
			final TransferTransaction transaction = this.createTransferTransaction(signer, recipient, 99, (Message)null);
			transaction.setFee(Amount.fromNem(10));

			// Act:
			final TransactionObserver observer = Mockito.mock(TransactionObserver.class);
			transaction.undo(observer);

			// Assert:
			final ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
			Mockito.verify(observer, Mockito.times(3)).notify(notificationCaptor.capture());
			NotificationUtils.assertAccountNotification(notificationCaptor.getAllValues().get(2), recipient);
			NotificationUtils.assertBalanceTransferNotification(notificationCaptor.getAllValues().get(1), recipient, signer, Amount.fromNem(99));
			NotificationUtils.assertBalanceCreditNotification(notificationCaptor.getAllValues().get(0), signer, Amount.fromNem(10));
		}

		//endregion

		//region Secure Message Consistency

		@Test
		public void consistentSecureMessageCanBeDecoded() {
			// Arrange:
			final Account sender = Utils.generateRandomAccount();
			final Account senderPublicKeyOnly = Utils.createPublicOnlyKeyAccount(sender);
			final Account recipient = Utils.generateRandomAccount();
			final Account recipientPublicKeyOnly = Utils.createPublicOnlyKeyAccount(recipient);
			final Message message = SecureMessage.fromDecodedPayload(sender, recipientPublicKeyOnly, new byte[] { 1, 2, 3 });
			final TransferTransaction originalTransaction = this.createTransferTransaction(sender, recipientPublicKeyOnly, 1L, message);

			final MockAccountLookup accountLookup = new MockAccountLookup();
			accountLookup.setMockAccount(senderPublicKeyOnly);
			accountLookup.setMockAccount(recipient);

			// Act:
			final TransferTransaction transaction = createRoundTrippedTransaction(originalTransaction, accountLookup);

			// Assert:
			Assert.assertThat(transaction.getMessage().canDecode(), IsEqual.equalTo(true));
			Assert.assertThat(transaction.getMessage().getDecodedPayload(), IsEqual.equalTo(new byte[] { 1, 2, 3 }));
		}

		@Test
		public void secureMessageCannotBeDecodedWithoutSenderAndRecipientPrivateKey() {
			// Arrange:
			final Account sender = Utils.generateRandomAccount();
			final Account recipient = Utils.generateRandomAccount();
			final Message message = SecureMessage.fromDecodedPayload(sender, recipient, new byte[] { 1, 2, 3 });
			final TransferTransaction originalTransaction = this.createTransferTransaction(sender, recipient, 1L, message);

			final MockAccountLookup accountLookup = new MockAccountLookup();
			accountLookup.setMockAccount(Utils.createPublicOnlyKeyAccount(sender));
			accountLookup.setMockAccount(Utils.createPublicOnlyKeyAccount(recipient));

			// Act:
			final TransferTransaction transaction = createRoundTrippedTransaction(originalTransaction, accountLookup);

			// Assert:
			Assert.assertThat(transaction.getMessage().canDecode(), IsEqual.equalTo(false));
			Assert.assertThat(transaction.getMessage().getDecodedPayload(), IsNull.nullValue());
		}

		//endregion

		protected static TransferTransaction createRoundTrippedTransaction(
				final Transaction originalTransaction,
				final AccountLookup accountLookup) {
			// Act:
			final Deserializer deserializer = Utils.roundtripVerifiableEntity(originalTransaction, accountLookup);
			deserializer.readInt("type");
			return new TransferTransaction(VerifiableEntity.DeserializationOptions.VERIFIABLE, deserializer);
		}

		protected static Collection<MosaicTransferPair> createSmartTiles() {
			return IntStream.range(0, 5).mapToObj(Utils::createMosaicTransferPair).collect(Collectors.toList());
		}
	}

	public static class AbstractTransferTransactionV1Test extends AbstractTransferTransactionTest {

		protected TransferTransaction createTransferTransaction(
				final Account sender,
				final Account recipient,
				final long amount,
				final TransferTransactionAttachment attachment) {
			return new TransferTransaction(1, TimeInstant.ZERO, sender, recipient, Amount.fromNem(amount), attachment);
		}

		//region deserialization

		@Test
		public void transactionCannotBeRoundTrippedWithoutMessageAndWithSmartTiles() {
			// Arrange:
			this.assertCannotBeRoundTripped(null);
		}

		@Test
		public void transactionCannotBeRoundTrippedWithMessageAndWithSmartTiles() {
			// Assert:
			this.assertCannotBeRoundTripped(new byte[] { 12, 50, 21 });
		}

		protected void assertCannotBeRoundTripped(final byte[] messageBytes) {
			// Arrange:
			final Account signer = Utils.generateRandomAccount();
			final Account recipient = Utils.generateRandomAccountWithoutPrivateKey();
			final Message message = null == messageBytes ? null : new PlainMessage(messageBytes);
			final TransferTransaction originalTransaction = this.createTransferTransaction(signer, recipient, 123, message, createSmartTiles());

			final MockAccountLookup accountLookup = MockAccountLookup.createWithAccounts(signer, recipient);
			final TransferTransaction transaction = createRoundTrippedTransaction(originalTransaction, accountLookup);

			// Assert: the mosaic transfers are not persisted in v1 transactions
			Assert.assertThat(originalTransaction.getAttachment().getMosaicTransfers().isEmpty(), IsEqual.equalTo(false));
			Assert.assertThat(transaction.getAttachment().getMosaicTransfers().isEmpty(), IsEqual.equalTo(true));
		}

		//endregion
	}

	public static class AbstractTransferTransactionV2Test extends AbstractTransferTransactionTest {

		protected TransferTransaction createTransferTransaction(
				final Account sender,
				final Account recipient,
				final long amount,
				final TransferTransactionAttachment attachment) {
			return new TransferTransaction(2, TimeInstant.ZERO, sender, recipient, Amount.fromNem(amount), attachment);
		}

		//region deserialization

		@Test
		public void transactionCanBeRoundTrippedWithoutMessageAndWithSmartTiles() {
			// Arrange:
			this.assertCanBeRoundTripped(null, createSmartTiles());
		}

		@Test
		public void transactionCanBeRoundTrippedWithMessageAndWithSmartTiles() {
			// Assert:
			this.assertCanBeRoundTripped(new byte[] { 12, 50, 21 }, createSmartTiles());
		}

		//endregion

		//region execute /undo

		@Test
		public void executeRaisesAppropriateNotificationsForSmartTileTransfers() {
			// Arrange:
			final Account signer = Utils.generateRandomAccount();
			final Account recipient = Utils.generateRandomAccount();
			final Transaction transaction = this.createTransferWithSmartTiles(signer, recipient);

			// Act:
			final TransactionObserver observer = Mockito.mock(TransactionObserver.class);
			transaction.execute(observer);

			// Assert:
			final ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
			Mockito.verify(observer, Mockito.times(7)).notify(notificationCaptor.capture());
			final List<Notification> notifications = notificationCaptor.getAllValues();

			NotificationUtils.assertAccountNotification(notifications.get(0), recipient);
			NotificationUtils.assertSmartTileTransferNotification(notifications.get(1), signer, recipient, Utils.createSmartTile(7, 12 * 20));
			NotificationUtils.assertAccountNotification(notifications.get(2), recipient);
			NotificationUtils.assertSmartTileTransferNotification(notifications.get(3), signer, recipient, Utils.createSmartTile(9, 24 * 20));
			NotificationUtils.assertAccountNotification(notifications.get(4), recipient);
			NotificationUtils.assertSmartTileTransferNotification(notifications.get(5), signer, recipient, Utils.createSmartTile(11, 5 * 20));
			NotificationUtils.assertBalanceDebitNotification(notifications.get(6), signer, Amount.fromNem(10));
		}

		@Test
		public void executeRaisesAppropriateNotificationsForSmartTileTransfersIncludingXem() {
			// Arrange:
			final Account signer = Utils.generateRandomAccount();
			final Account recipient = Utils.generateRandomAccount();
			final Transaction transaction = this.createTransferWithXemSmartTiles(signer, recipient);

			// Act:
			final TransactionObserver observer = Mockito.mock(TransactionObserver.class);
			transaction.execute(observer);

			// Assert:
			final ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
			Mockito.verify(observer, Mockito.times(7)).notify(notificationCaptor.capture());
			final List<Notification> notifications = notificationCaptor.getAllValues();

			NotificationUtils.assertAccountNotification(notifications.get(0), recipient);
			NotificationUtils.assertSmartTileTransferNotification(notifications.get(1), signer, recipient, Utils.createSmartTile(7, 12 * 20));
			NotificationUtils.assertAccountNotification(notifications.get(2), recipient);
			NotificationUtils.assertBalanceTransferNotification(notifications.get(3), signer, recipient, Amount.fromMicroNem(24 * 20));
			NotificationUtils.assertAccountNotification(notifications.get(4), recipient);
			NotificationUtils.assertSmartTileTransferNotification(notifications.get(5), signer, recipient, Utils.createSmartTile(11, 5 * 20));
			NotificationUtils.assertBalanceDebitNotification(notifications.get(6), signer, Amount.fromNem(10));
		}

		@Test
		public void undoRaisesAppropriateNotificationsForSmartTileTransfers() {
			// Arrange:
			final Account signer = Utils.generateRandomAccount();
			final Account recipient = Utils.generateRandomAccount();
			final Transaction transaction = this.createTransferWithSmartTiles(signer, recipient);

			// Act:
			final TransactionObserver observer = Mockito.mock(TransactionObserver.class);
			transaction.undo(observer);

			// Assert:
			final ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
			Mockito.verify(observer, Mockito.times(7)).notify(notificationCaptor.capture());
			final List<Notification> notifications = notificationCaptor.getAllValues();

			NotificationUtils.assertAccountNotification(notifications.get(6), recipient);
			NotificationUtils.assertSmartTileTransferNotification(notifications.get(5), recipient, signer, Utils.createSmartTile(7, 12 * 20));
			NotificationUtils.assertAccountNotification(notifications.get(4), recipient);
			NotificationUtils.assertSmartTileTransferNotification(notifications.get(3), recipient, signer, Utils.createSmartTile(9, 24 * 20));
			NotificationUtils.assertAccountNotification(notifications.get(2), recipient);
			NotificationUtils.assertSmartTileTransferNotification(notifications.get(1), recipient, signer, Utils.createSmartTile(11, 5 * 20));
			NotificationUtils.assertBalanceCreditNotification(notifications.get(0), signer, Amount.fromNem(10));
		}

		@Test
		public void undoRaisesAppropriateNotificationsForSmartTileTransfersIncludingXem() {
			// Arrange:
			final Account signer = Utils.generateRandomAccount();
			final Account recipient = Utils.generateRandomAccount();
			final Transaction transaction = this.createTransferWithXemSmartTiles(signer, recipient);

			// Act:
			final TransactionObserver observer = Mockito.mock(TransactionObserver.class);
			transaction.undo(observer);

			// Assert:
			final ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
			Mockito.verify(observer, Mockito.times(7)).notify(notificationCaptor.capture());
			final List<Notification> notifications = notificationCaptor.getAllValues();

			NotificationUtils.assertAccountNotification(notifications.get(6), recipient);
			NotificationUtils.assertSmartTileTransferNotification(notifications.get(5), recipient, signer, Utils.createSmartTile(7, 12 * 20));
			NotificationUtils.assertAccountNotification(notifications.get(4), recipient);
			NotificationUtils.assertBalanceTransferNotification(notifications.get(3), recipient, signer, Amount.fromMicroNem(24 * 20));
			NotificationUtils.assertAccountNotification(notifications.get(2), recipient);
			NotificationUtils.assertSmartTileTransferNotification(notifications.get(1), recipient, signer, Utils.createSmartTile(11, 5 * 20));
			NotificationUtils.assertBalanceCreditNotification(notifications.get(0), signer, Amount.fromNem(10));
		}

		private Transaction createTransferWithXemSmartTiles(final Account signer, final Account recipient) {
			final Collection<MosaicTransferPair> pairs = Arrays.asList(
					Utils.createMosaicTransferPair(7, 12),
					new MosaicTransferPair(Utils.createMosaic("nem", "xem").getId(), new Quantity(24)),
					Utils.createMosaicTransferPair(9, 24));
			final TransferTransactionAttachment attachment = new TransferTransactionAttachment();
			pairs.forEach(attachment::addMosaicTransfer);
			final Transaction transaction = this.createTransferTransaction(signer, recipient, 20, attachment);
			transaction.setFee(Amount.fromNem(10));
			return transaction;
		}

		private Transaction createTransferWithSmartTiles(final Account signer, final Account recipient) {
			final Collection<MosaicTransferPair> pairs = Arrays.asList(
					Utils.createMosaicTransferPair(7, 12),
					Utils.createMosaicTransferPair(11, 5),
					Utils.createMosaicTransferPair(9, 24));
			final TransferTransactionAttachment attachment = new TransferTransactionAttachment();
			pairs.forEach(attachment::addMosaicTransfer);
			final Transaction transaction = this.createTransferTransaction(signer, recipient, 20, attachment);
			transaction.setFee(Amount.fromNem(10));
			return transaction;
		}

		//endregion
	}
}
