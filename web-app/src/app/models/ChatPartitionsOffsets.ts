import { PartitionOffset } from "./PartitionOffset";

export interface ChatPartitionsOffsets {
    chatId: string;
    partitionOffset: PartitionOffset[]
}