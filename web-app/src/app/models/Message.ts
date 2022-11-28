export interface Message {
    content: string;
    authorId: string;
    authorLogin: string;
    utcTime: number;
    zoneId: string;
    chatId: string;
    chatName: string;
    groupChat: boolean;    
}