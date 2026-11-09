const el = document.getElementById('b');
window.headsup.onBanner(({ message, bgColor, textColor }) => {
  el.textContent = message;
  el.style.background = bgColor;
  el.style.color = textColor;
  // 애니메이션은 위치만 움직이고 투명도는 건드리지 않는다 (항상 보이게)
  el.classList.remove('in');
  void el.offsetWidth;
  el.classList.add('in');
});
