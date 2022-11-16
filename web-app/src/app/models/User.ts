


export interface User {
    user_id: string;
    login: string;
}


/*
rooting  w kessengerze,
Endpointy:

/ - main (niechroniony) po wejściu strona z tworzeniem konta i logowaniem. 

/user - userComponent (chroniony) czaty po lewej stronie 

/user/:chatId - podrouting /user zawierający dodatkowo wyświetlanie czatu

/user/:chatId/edit - podrouting zawierający zamiast czatu stronę z edycją 
                     czatu np zmiana nazwy czatu. Po przetworzeniu i przerobieniu 
                     przez serwer przekierować na stronę czatu /user/:chatId 
                     Tutaj też powinno być OPUSZCZANIE CZATU jeśli jest on grupowy

/userEdit - strona z edycją loginu i hasła po wysłaniu formularza i poprawnym
           jego przetworzeniu przekierować na /user                     

/createNewChat - (chroniony) formularz z wybieraniem userów oraz nazwą czatu.
           po stworzeniu czatu przekierowanie na /user




/** = pagenotfoundcomponent - component z info, że żadna strona nie pasuje wraz  
    z opuźnieniem kilku sekund, po którym następuje przekierowanie do / 


 */

