import { PartitionOffset } from "./PartitionOffset";

export interface Message {
    content: string;
    authorId: string;
    authorLogin: string;
    sendingTime: number;
    serverTime: number;
    zoneId: string;
    chatId: string;
    chatName: string;
    groupChat: boolean;    
    partOff: PartitionOffset;
}