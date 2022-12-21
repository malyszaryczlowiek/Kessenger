export interface Message {
    content: string;
    authorId: string;
    authorLogin: string;
    chatId: string;
    chatName: string;
    groupChat: boolean;    
    utcTime: number;
    zoneId: string;
}