import { PartitionOffset } from "./PartitionOffset";

export interface ChatOffsetUpdate {
    userId: string;
    chatId: string;
    lastMessageTime: number;
    partitionOffsets: Array<PartitionOffset>;
}