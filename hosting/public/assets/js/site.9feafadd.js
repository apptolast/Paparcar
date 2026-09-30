/**
 * Everything the page needs at runtime, and nothing else: a sticky-header state, the mobile menu,
 * and the scroll-in reveal.
 *
 * The page is fully readable and navigable with this file blocked — that is the contract. The
 * reveal animation only ever ARMS itself (by adding `js` to <html>) once it knows it can also
 * disarm itself, so a browser without IntersectionObserver, or a visitor who asked for reduced
 * motion, never ends up staring at hidden content.
 */
(function () {
  'use strict';

  var root = document.documentElement;
  var reduced = window.matchMedia('(prefers-reduced-motion: reduce)');

  // ── sticky header ──────────────────────────────────────────────────────────
  var header = document.querySelector('[data-header]');
  if (header) {
    var onScroll = function () {
      if (window.scrollY > 8) header.setAttribute('data-stuck', '');
      else header.removeAttribute('data-stuck');
    };
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
  }

  // ── mobile menu ────────────────────────────────────────────────────────────
  var toggle = document.querySelector('[data-menu-toggle]');
  var menu = document.querySelector('[data-menu]');

  if (toggle && menu) {
    var setOpen = function (open) {
      toggle.setAttribute('aria-expanded', String(open));
      toggle.setAttribute('aria-label', toggle.getAttribute(open ? 'data-label-close' : 'data-label-open'));
      menu.hidden = !open;
      document.body.style.overflow = open ? 'hidden' : '';
    };

    toggle.addEventListener('click', function () {
      setOpen(toggle.getAttribute('aria-expanded') !== 'true');
    });

    menu.addEventListener('click', function (event) {
      if (event.target.closest('a')) setOpen(false);
    });

    document.addEventListener('keydown', function (event) {
      if (event.key === 'Escape' && toggle.getAttribute('aria-expanded') === 'true') {
        setOpen(false);
        toggle.focus();
      }
    });

    // The menu is a phone-only surface; if the viewport grows past the breakpoint while it is
    // open, the desktop nav takes over and the panel must not keep the page scroll locked.
    window.matchMedia('(min-width: 900px)').addEventListener('change', function (event) {
      if (event.matches) setOpen(false);
    });
  }

  // ── reveal on scroll ───────────────────────────────────────────────────────
  var targets = document.querySelectorAll('[data-reveal]');
  if (!targets.length || !('IntersectionObserver' in window) || reduced.matches) return;

  root.classList.add('js');

  var observer = new IntersectionObserver(
    function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        entry.target.classList.add('is-in');
        observer.unobserve(entry.target);
      });
    },
    // threshold 0, deliberately: a threshold of "8% of the element" never fires for a block taller
    // than the viewport, which would leave that block invisible forever. The rootMargin is what
    // delays the reveal until the element is properly on screen.
    { rootMargin: '0px 0px -10% 0px', threshold: 0 },
  );

  targets.forEach(function (el) { observer.observe(el); });

  // Anything already on screen at load reveals immediately, so the first paint is not empty.
  requestAnimationFrame(function () {
    targets.forEach(function (el) {
      if (el.getBoundingClientRect().top < window.innerHeight) el.classList.add('is-in');
    });
  });
})();
