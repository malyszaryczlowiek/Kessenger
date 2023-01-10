export interface ChatOffsetUpdate {
    userId: string;
    chatId: string;
    lastMessageTime: number;
    partitionOffsets: Array<{partition: number, offset: number}>;
}