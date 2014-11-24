/* Service Main Calls */
var IDService = new IdService();

function IdService() {
    this.host = 'https://authservice-ua-es.azurewebsites.net/';
    //this.host = 'https://localhost:44300/';
    this.init = false;
    this.clientId = '';
    this.cookie = false;
    this.redirect_url = '';
    this.access_token = {};
}

/*
 {
 clientid : "CLIENT_ID",
 cookie  : true/false,
 redirect_url: "REDIRECT_URL"
 }
 */

IdService.prototype.Init = function (object) {
    object = (typeof object !== "object") ? {} : object;
    this.clientId = object.clientid;
    this.cookie = object.cookie || false;
    this.redirect_url = object.redirect_url || '';
    this.init = true;
    IDService.CreateCookie(IDService.GetUserInfo);
};

IdService.prototype.GetUserInfo = function () {
    if (!this.init) return;
    $.ajax({
        url: this.host + 'Auth/UserInfo/' + this.clientId,
        headers: { 'Cookie': document.cookie },
        crossDomain: true,
        xhrFields: {
            withCredentials: true
        },
        success: function (data) {
            IDService.SaveData(data);
        }
    });
};

IdService.prototype.Login = function () {
    if (!this.init) return;
    var loginurl = this.host + 'Auth/Login/?clientid=' + this.clientId;
    if (this.redirect_url.length > 1)
        loginurl = loginurl + '&redirect_url=' + encodeURIComponent(this.redirect_url);
    loginurl = loginurl + '&cookie=' + ((this.cookie) ? 'true' : 'false');
    if (this.redirect_url.length == 0) {
        var win = window.open(loginurl, '', 'scrollbars=no,menubar=no,toolbar=no,status=no');
        var timer = setInterval(function() {
            if (win.closed) {
                clearInterval(timer);
            }
        }, 250);
    } else window.location.href = loginurl;
};

IdService.prototype.Logoff = function () {
    $.ajax({
        url: this.host + 'Auth/Logout/' + this.clientId,
        headers: { 'Cookie': document.cookie },
        crossDomain: true,
        xhrFields: {
            withCredentials: true
        }
    }).done((function () {
        this.access_token = {};
        location.reload();
    }).bind(this));
};

IdService.prototype.CreateCookie = function (callback) {
    $.ajax({
        type: 'POST',
        url: this.host + 'Auth/RegDomain/' + this.clientId,
        headers: { 'Cookie': document.cookie },
        data: { "domain": document.location.host },
        crossDomain: true,
        xhrFields: {
            withCredentials: true
        }
    })
        .done(callback.bind(this))
        .fail((function () { this.init = false; }).bind(this));
};

IdService.prototype.SaveData = function (data) {
    this.access_token = data;
};